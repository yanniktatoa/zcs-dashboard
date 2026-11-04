#!/bin/bash
# ============================================================
#  compare.sh — Compare les résultats JVM vs Native Image
#  À lancer après avoir exécuté benchmark.sh en mode jvm ET native
# ============================================================

RESULTS_DIR="$HOME/benchmark-results"

# Trouver les deux derniers runs (jvm et native)
JVM_RUN=$(ls -t "$RESULTS_DIR" | xargs -I{} sh -c 'ls '"$RESULTS_DIR"'/{}/SUMMARY-jvm.md 2>/dev/null && echo {}' | head -1 | xargs dirname 2>/dev/null || echo "")
NATIVE_RUN=$(ls -t "$RESULTS_DIR" | xargs -I{} sh -c 'ls '"$RESULTS_DIR"'/{}/SUMMARY-native.md 2>/dev/null && echo {}' | head -1 | xargs dirname 2>/dev/null || echo "")

if [ -z "$JVM_RUN" ] || [ -z "$NATIVE_RUN" ]; then
  echo "[ERREUR] Impossible de trouver les deux runs JVM et Native dans $RESULTS_DIR"
  echo "Lance d'abord : ./benchmark.sh jvm && ./benchmark.sh native"
  exit 1
fi

echo ""
echo "════════════════════════════════════════════════════"
echo "  Comparaison JVM vs GraalVM Native Image"
echo "  JVM    : $JVM_RUN"
echo "  Native : $NATIVE_RUN"
echo "════════════════════════════════════════════════════"
echo ""

# ─── Taille des images ───────────────────────────────────────
echo "## 1. Taille des images Docker (MB)"
echo ""
printf "%-25s %10s %10s %10s\n" "Service" "JVM (MB)" "Native (MB)" "Gain"
printf "%-25s %10s %10s %10s\n" "-------" "--------" "-----------" "----"

while IFS=',' read -r svc tag size_mb created; do
  [ "$svc" = "service" ] && continue
  native_size=$(grep "^${svc}," "$NATIVE_RUN/images/image-sizes-native.csv" 2>/dev/null | cut -d',' -f3 || echo "N/A")
  if [ "$native_size" != "N/A" ] && [ "$size_mb" != "0" ]; then
    gain=$(echo "scale=1; (($size_mb - $native_size) / $size_mb) * 100" | bc)
    printf "%-25s %10s %11s %9s%%\n" "$svc" "$size_mb" "$native_size" "$gain"
  else
    printf "%-25s %10s %11s %10s\n" "$svc" "$size_mb" "$native_size" "N/A"
  fi
done < "$JVM_RUN/images/image-sizes-jvm.csv"

echo ""

# ─── Startup Time ────────────────────────────────────────────
echo "## 2. Startup Time (ms)"
echo ""
printf "%-25s %10s %12s %10s\n" "Service" "JVM (ms)" "Native (ms)" "Gain"
printf "%-25s %10s %12s %10s\n" "-------" "--------" "-----------" "----"

while IFS=',' read -r svc startup_ms started_at; do
  [ "$svc" = "service" ] && continue
  native_ms=$(grep "^${svc}," "$NATIVE_RUN/startup/startup-native.csv" 2>/dev/null | cut -d',' -f2 || echo "N/A")
  if [ "$native_ms" != "N/A" ] && [ "$startup_ms" != "0" ]; then
    gain=$(echo "scale=1; (($startup_ms - $native_ms) / $startup_ms) * 100" | bc)
    printf "%-25s %10s %12s %9s%%\n" "$svc" "$startup_ms" "$native_ms" "$gain"
  else
    printf "%-25s %10s %12s %10s\n" "$svc" "$startup_ms" "$native_ms" "N/A"
  fi
done < "$JVM_RUN/startup/startup-jvm.csv"

echo ""

# ─── Cold Start ──────────────────────────────────────────────
echo "## 3. Cold Start — Scale-to-Zero (ms, moyenne sur 3 essais)"
echo ""
printf "%-25s %10s %12s %10s\n" "Service" "JVM (ms)" "Native (ms)" "Gain"
printf "%-25s %10s %12s %10s\n" "-------" "--------" "-----------" "----"

for svc in catalog-service user-service loan-service library-gateway; do
  jvm_avg=$(grep "^${svc}," "$JVM_RUN/coldstart/coldstart-jvm.csv" 2>/dev/null \
    | awk -F',' '{sum+=$3; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}')
  native_avg=$(grep "^${svc}," "$NATIVE_RUN/coldstart/coldstart-native.csv" 2>/dev/null \
    | awk -F',' '{sum+=$3; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}')
  if [ "$jvm_avg" != "N/A" ] && [ "$native_avg" != "N/A" ] && [ "$jvm_avg" != "0" ]; then
    gain=$(echo "scale=1; (($jvm_avg - $native_avg) / $jvm_avg) * 100" | bc)
    printf "%-25s %10s %12s %9s%%\n" "$svc" "$jvm_avg" "$native_avg" "$gain"
  else
    printf "%-25s %10s %12s %10s\n" "$svc" "${jvm_avg:-N/A}" "${native_avg:-N/A}" "N/A"
  fi
done

echo ""

# ─── Charge JMeter ───────────────────────────────────────────
echo "## 4. Test de charge JMeter"
echo ""
printf "%-25s %10s %12s\n" "Métrique" "JVM" "Native"
printf "%-25s %10s %12s\n" "--------" "---" "------"

for metric in total_requests errors avg_latency_ms throughput_rps; do
  jvm_val=$(grep "^${metric}," "$JVM_RUN/load/load-summary-jvm.csv" 2>/dev/null | cut -d',' -f2 || echo "N/A")
  native_val=$(grep "^${metric}," "$NATIVE_RUN/load/load-summary-native.csv" 2>/dev/null | cut -d',' -f2 || echo "N/A")
  printf "%-25s %10s %12s\n" "$metric" "${jvm_val:-N/A}" "${native_val:-N/A}"
done

echo ""
echo "════════════════════════════════════════════════════"
echo "  Rapport complet dans :"
echo "  JVM    → $JVM_RUN/SUMMARY-jvm.md"
echo "  Native → $NATIVE_RUN/SUMMARY-native.md"
echo "════════════════════════════════════════════════════"
