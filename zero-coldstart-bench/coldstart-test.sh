#!/bin/bash
RESULTS_DIR="$HOME/benchmark-results"
RUN_DIR=$(ls -t "$RESULTS_DIR" | head -1)
RUN_DIR="$RESULTS_DIR/$RUN_DIR"
MODE="jvm"
NAMESPACE="library"
SERVICES=("catalog-service" "user-service" "loan-service" "library-gateway")
TRIALS=3

mkdir -p "$RUN_DIR/coldstart"
OUT="$RUN_DIR/coldstart/coldstart-${MODE}.csv"
echo "service,trial,cold_start_s,ready_at" > "$OUT"

echo "══════════════════════════════════════"
echo "  Cold Start — Scale-to-Zero ($MODE)"
echo "══════════════════════════════════════"

for svc in "${SERVICES[@]}"; do
  echo ""
  echo "→ $svc"
  for trial in $(seq 1 $TRIALS); do
    kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=0 > /dev/null 2>&1
    kubectl wait --for=delete pod -l app="$svc" -n "$NAMESPACE" --timeout=60s > /dev/null 2>&1 || true
    sleep 2

    T_START=$(date +%s)
    kubectl scale deployment "$svc" -n "$NAMESPACE" --replicas=1 > /dev/null 2>&1
    kubectl wait --for=condition=ready pod -l app="$svc" -n "$NAMESPACE" --timeout=180s > /dev/null 2>&1
    T_END=$(date +%s)

    SECONDS_ELAPSED=$((T_END - T_START))
    READY_AT=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "${svc},${trial},${SECONDS_ELAPSED},${READY_AT}" >> "$OUT"
    echo "  Essai $trial → ${SECONDS_ELAPSED}s"
    sleep 5
  done
done

echo ""
echo "══════════════════════════════════════"
echo "  Résumé Cold Start JVM"
echo "══════════════════════════════════════"
for svc in "${SERVICES[@]}"; do
  AVG=$(grep "^${svc}," "$OUT" | awk -F',' '{sum+=$3; count++} END {printf "%.0f", sum/count}')
  echo "  $svc → moyenne ${AVG}s"
done
echo ""
echo "Sauvegardé → $OUT"
