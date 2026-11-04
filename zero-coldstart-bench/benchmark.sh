#!/bin/bash
# ============================================================
#  benchmark.sh — Zero Cold Start · Mémoire Master 2
#  Compare JVM classique vs GraalVM Native Image
#  Auteur : Yannik · Cluster k3d : zero-coldstart
# ============================================================

set -euo pipefail

# ─── Configuration ───────────────────────────────────────────
NAMESPACE="library"
GATEWAY_URL="http://localhost:30080"
RESULTS_DIR="$HOME/benchmark-results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RUN_DIR="$RESULTS_DIR/$TIMESTAMP"
JMETER_HOME="/opt/jmeter"
SERVICES=("catalog-service" "user-service" "loan-service" "library-gateway")
MODE="${1:-jvm}"          # Paramètre : jvm | native
LOAD_DURATION=60          # Durée test de charge JMeter (secondes)
LOAD_THREADS=20           # Threads simultanés JMeter
LOAD_RAMP=10              # Ramp-up JMeter (secondes)
WARMUP_REQUESTS=50        # Requêtes de chauffe avant mesure

# Couleurs pour les logs
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'

log()    { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC} $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail()   { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }
section(){ echo -e "\n${CYAN}══════════════════════════════════════${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}══════════════════════════════════════${NC}"; }

# ─── Vérifications préalables ────────────────────────────────
check_prerequisites() {
  section "Vérification de l'environnement"
  command -v kubectl &>/dev/null || fail "kubectl introuvable"
  command -v docker  &>/dev/null || fail "docker introuvable"
  command -v jmeter  &>/dev/null || fail "JMeter introuvable — source ~/.bashrc ?"
  kubectl cluster-info &>/dev/null || fail "Cluster k3d non joignable"
  ok "Tous les outils sont disponibles"
  log "Mode de benchmark : ${YELLOW}${MODE}${NC}"
  log "Résultats dans    : $RUN_DIR"
}

# ─── Initialisation des répertoires ──────────────────────────
init_dirs() {
  mkdir -p "$RUN_DIR"/{startup,memory,images,load,coldstart,raw}
  log "Répertoires créés dans $RUN_DIR"
}

# ─── 1. Taille des images Docker ─────────────────────────────
measure_image_sizes() {
  section "1/5 — Taille des images Docker ($MODE)"
  local out="$RUN_DIR/images/image-sizes-${MODE}.csv"
  echo "service,tag,size_mb,created" > "$out"

  for svc in "${SERVICES[@]}"; do
    local tag="${svc}:${MODE}"
    local size=$(docker image inspect "$tag" --format='{{.Size}}' 2>/dev/null || echo "0")
    local size_mb=$(echo "scale=2; $size / 1048576" | bc)
    local created=$(docker image inspect "$tag" --format='{{.Created}}' 2>/dev/null || echo "N/A")
    echo "${svc},${tag},${size_mb},${created}" >> "$out"
    log "  ${svc} → ${size_mb} MB"
  done
  ok "Tailles images enregistrées → $out"
}

# ─── 2. Startup Time ─────────────────────────────────────────
measure_startup_time() {
  section "2/5 — Startup Time ($MODE)"
  local out="$RUN_DIR/startup/startup-${MODE}.csv"
  echo "service,startup_ms,started_at" > "$out"

  for svc in "${SERVICES[@]}"; do
    log "Mesure startup de $svc..."

    # Scale à 0 puis relance pour forcer un vrai cold start
    kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=0 &>/dev/null
    kubectl wait --for=delete pod -l app="$svc" -n "$NAMESPACE" --timeout=60s &>/dev/null || true
    sleep 2

    local t_start=$(date +%s%N)
    kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=1 &>/dev/null

    # Attendre que le pod soit Ready
    kubectl wait --for=condition=ready pod -l app="$svc" -n "$NAMESPACE" --timeout=120s &>/dev/null

    local t_end=$(date +%s%N)
    local startup_ms=$(( (t_end - t_start) / 1000000 ))

    # Chercher la ligne "Started ... in X.XXX seconds" dans les logs
    local log_startup=$(kubectl logs -l app="$svc" -n "$NAMESPACE" --tail=50 2>/dev/null \
      | grep -oP "Started \w+ in \K[\d.]+" | head -1 || echo "N/A")

    local started_at=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "${svc},${startup_ms},${started_at}" >> "$out"
    log "  ${svc} → ${startup_ms} ms (Spring log: ${log_startup}s)"
  done
  ok "Startup times enregistrés → $out"
}

# ─── 3. Consommation mémoire ─────────────────────────────────
measure_memory() {
  section "3/5 — Consommation mémoire ($MODE)"
  local out="$RUN_DIR/memory/memory-${MODE}.csv"
  echo "service,phase,cpu_cores,memory_mi" > "$out"

  # Attendre que tous les pods soient stables
  sleep 10

  log "Mesure mémoire à vide (idle)..."
  kubectl top pods -n "$NAMESPACE" --no-headers 2>/dev/null | while read -r pod cpu mem; do
    # Extraire le nom du service depuis le nom du pod
    local svc=$(echo "$pod" | grep -oP "^[a-z-]+-service|library-gateway" || echo "$pod")
    local cpu_clean=$(echo "$cpu" | tr -d 'm')
    local mem_clean=$(echo "$mem" | tr -d 'Mi')
    echo "${svc},idle,${cpu_clean},${mem_clean}" >> "$out"
  done

  # Chauffe avec quelques requêtes
  log "Envoi de $WARMUP_REQUESTS requêtes de chauffe..."
  for i in $(seq 1 $WARMUP_REQUESTS); do
    curl -s "${GATEWAY_URL}/api/books" -o /dev/null || true
    curl -s "${GATEWAY_URL}/api/users" -o /dev/null || true
  done
  sleep 5

  log "Mesure mémoire sous chauffe (warm)..."
  kubectl top pods -n "$NAMESPACE" --no-headers 2>/dev/null | while read -r pod cpu mem; do
    local svc=$(echo "$pod" | grep -oP "^[a-z-]+-service|library-gateway" || echo "$pod")
    local cpu_clean=$(echo "$cpu" | tr -d 'm')
    local mem_clean=$(echo "$mem" | tr -d 'Mi')
    echo "${svc},warm,${cpu_clean},${mem_clean}" >> "$out"
  done

  ok "Mémoire enregistrée → $out"
}

# ─── 4. Test de charge JMeter ────────────────────────────────
generate_jmeter_plan() {
  local plan="$RUN_DIR/load/test-plan.jmx"
  cat > "$plan" << JMXEOF
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Library API Benchmark" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments"/>
    </TestPlan>
    <hashTree>

      <!-- ── Thread Group : GET /api/books ── -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="GET books" enabled="true">
        <intProp name="ThreadGroup.num_threads">${LOAD_THREADS}</intProp>
        <intProp name="ThreadGroup.ramp_time">${LOAD_RAMP}</intProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">${LOAD_DURATION}</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">true</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api/books" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">30080</stringProp>
          <stringProp name="HTTPSampler.path">/api/books</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary" enabled="true">
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              
              <threadName>false</threadName>
              <dataType>false</dataType>
              <encoding>false</encoding>
              <assertions>false</assertions>
              <bytes>true</bytes>
              <sentBytes>false</sentBytes>
              <url>false</url>
              <fileName>false</fileName>
              <hostname>false</hostname>
              <threadCounts>true</threadCounts>
              <sampleCount>true</sampleCount>
            </value>
          </objProp>
          <stringProp name="filename">$RUN_DIR/load/results-${MODE}.jtl</stringProp>
        </ResultCollector>
        <hashTree/>
      </hashTree>

      <!-- ── Thread Group : GET /api/users ── -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="GET users" enabled="true">
        <intProp name="ThreadGroup.num_threads">${LOAD_THREADS}</intProp>
        <intProp name="ThreadGroup.ramp_time">${LOAD_RAMP}</intProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">${LOAD_DURATION}</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">true</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api/users" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">30080</stringProp>
          <stringProp name="HTTPSampler.path">/api/users</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>

    </hashTree>
  </hashTree>
</jmeterTestPlan>
JMXEOF
  echo "$plan"
}

run_load_test() {
  section "4/5 — Test de charge JMeter ($MODE)"
  local plan=$(generate_jmeter_plan)
  local report_dir="$RUN_DIR/load/report-${MODE}"

  log "Lancement JMeter — ${LOAD_THREADS} threads · ${LOAD_DURATION}s · ${LOAD_RAMP}s ramp-up"
  jmeter -n \
    -t "$plan" \
    -l "$RUN_DIR/load/results-${MODE}.jtl" \
    -e -o "$report_dir" \
    -Jsummariser.interval=10 \
    2>&1 | tee "$RUN_DIR/raw/jmeter-${MODE}.log"

  # Extraire les métriques clés du .jtl
  local out="$RUN_DIR/load/load-summary-${MODE}.csv"
  echo "metric,value" > "$out"

  if [ -f "$RUN_DIR/load/results-${MODE}.jtl" ]; then
    local total=$(tail -n +2 "$RUN_DIR/load/results-${MODE}.jtl" | wc -l)
    local errors=$(tail -n +2 "$RUN_DIR/load/results-${MODE}.jtl" | awk -F',' '$8=="false"' | wc -l)
    local avg_latency=$(tail -n +2 "$RUN_DIR/load/results-${MODE}.jtl" | awk -F',' '{sum+=$2; count++} END {printf "%.0f", sum/count}')
    local throughput=$(echo "scale=2; $total / $LOAD_DURATION" | bc)

    echo "total_requests,${total}" >> "$out"
    echo "errors,${errors}" >> "$out"
    echo "avg_latency_ms,${avg_latency}" >> "$out"
    echo "throughput_rps,${throughput}" >> "$out"

    log "  Total requêtes  : $total"
    log "  Erreurs         : $errors"
    log "  Latence moyenne : ${avg_latency} ms"
    log "  Throughput      : ${throughput} req/s"
  fi

  ok "Test de charge terminé → rapport HTML dans $report_dir"
}

# ─── 5. Cold Start (Scale-to-Zero) ───────────────────────────
measure_cold_start() {
  section "5/5 — Scénario Zero Cold Start ($MODE)"
  local out="$RUN_DIR/coldstart/coldstart-${MODE}.csv"
  echo "service,trial,cold_start_ms,ready_at" > "$out"
  local TRIALS=3   # Nombre de mesures par service pour la fiabilité

  for svc in "${SERVICES[@]}"; do
    log "Cold start de $svc ($TRIALS essais)..."

    for trial in $(seq 1 $TRIALS); do
      # Scale à zéro
      kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=0 &>/dev/null
      kubectl wait --for=delete pod -l app="$svc" -n "$NAMESPACE" \
        --timeout=60s &>/dev/null || true
      sleep 3

      # Mesure du temps de remontée
      local t_start=$(date +%s%N)
      kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=1 &>/dev/null
      kubectl wait --for=condition=ready pod -l app="$svc" \
        -n "$NAMESPACE" --timeout=120s &>/dev/null
      local t_end=$(date +%s%N)

      local cold_ms=$(( (t_end - t_start) / 1000000 ))
      local ready_at=$(date +"%Y-%m-%dT%H:%M:%S")
      echo "${svc},${trial},${cold_ms},${ready_at}" >> "$out"
      log "  ${svc} · essai ${trial} → ${cold_ms} ms"
      sleep 5
    done
  done
  ok "Cold starts enregistrés → $out"
}

# ─── Rapport de synthèse ─────────────────────────────────────
generate_summary() {
  section "Génération du rapport de synthèse"
  local out="$RUN_DIR/SUMMARY-${MODE}.md"

  cat > "$out" << EOF
# Benchmark Results — Mode : ${MODE^^}
Date : $(date "+%Y-%m-%d %H:%M:%S")
Cluster : zero-coldstart (k3d)
Namespace : $NAMESPACE

## Images Docker
$(cat "$RUN_DIR/images/image-sizes-${MODE}.csv" 2>/dev/null || echo "N/A")

## Startup Time
$(cat "$RUN_DIR/startup/startup-${MODE}.csv" 2>/dev/null || echo "N/A")

## Mémoire (idle + warm)
$(cat "$RUN_DIR/memory/memory-${MODE}.csv" 2>/dev/null || echo "N/A")

## Test de charge
$(cat "$RUN_DIR/load/load-summary-${MODE}.csv" 2>/dev/null || echo "N/A")

## Cold Start (scale-to-zero)
$(cat "$RUN_DIR/coldstart/coldstart-${MODE}.csv" 2>/dev/null || echo "N/A")
EOF

  ok "Rapport généré → $out"
  echo ""
  echo -e "${GREEN}══════════════════════════════════════${NC}"
  echo -e "${GREEN}  Benchmark ${MODE^^} terminé !${NC}"
  echo -e "${GREEN}  Résultats : $RUN_DIR${NC}"
  echo -e "${GREEN}══════════════════════════════════════${NC}"
}

# ─── Point d'entrée ──────────────────────────────────────────
main() {
  echo ""
  echo -e "${CYAN}  Zero Cold Start Benchmark — ${MODE^^}${NC}"
  echo -e "${CYAN}  $(date)${NC}"
  echo ""

  check_prerequisites
  init_dirs
  measure_image_sizes
  measure_startup_time
  measure_memory
  run_load_test
  measure_cold_start
  generate_summary
}

main "$@"
