#!/bin/bash
RESULTS_DIR="$HOME/benchmark-results"
RUN_DIR=$(ls -t "$RESULTS_DIR" | head -1)
RUN_DIR="$RESULTS_DIR/$RUN_DIR"
MODE="jvm"
DURATION=60
CONCURRENCY=10

echo "Test de charge — ${CONCURRENCY} requêtes parallèles · ${DURATION}s"
mkdir -p "$RUN_DIR/load"

COUNT=0
ERRORS=0
LATENCIES=""

START=$(date +%s)
END=$((START + DURATION))

while [ $(date +%s) -lt $END ]; do
  for i in $(seq 1 $CONCURRENCY); do
    RESULT=$(curl -s -o /dev/null -w "%{http_code} %{time_total}" http://localhost:8080/api/books)
    CODE=$(echo $RESULT | cut -d' ' -f1)
    TIME_S=$(echo $RESULT | cut -d' ' -f2)
    TIME_MS=$(echo "$TIME_S * 1000" | bc | cut -d'.' -f1)
    LATENCIES="$LATENCIES $TIME_MS"
    COUNT=$((COUNT + 1))
    if [ "$CODE" != "200" ]; then
      ERRORS=$((ERRORS + 1))
    fi
  done
done

AVG_MS=$(echo $LATENCIES | tr ' ' '\n' | awk '{sum+=$1; count++} END {printf "%.0f", sum/count}')
THROUGHPUT=$(echo "scale=2; $COUNT / $DURATION" | bc)

echo ""
echo "══════════════════════════════════"
echo "  Résultats Test de Charge JVM"
echo "══════════════════════════════════"
echo "  Total requêtes  : $COUNT"
echo "  Erreurs         : $ERRORS"
echo "  Latence moyenne : ${AVG_MS} ms"
echo "  Throughput      : ${THROUGHPUT} req/s"
echo "══════════════════════════════════"

echo "total_requests,${COUNT}" > "$RUN_DIR/load/load-summary-${MODE}.csv"
echo "errors,${ERRORS}" >> "$RUN_DIR/load/load-summary-${MODE}.csv"
echo "avg_latency_ms,${AVG_MS}" >> "$RUN_DIR/load/load-summary-${MODE}.csv"
echo "throughput_rps,${THROUGHPUT}" >> "$RUN_DIR/load/load-summary-${MODE}.csv"
echo "Sauvegardé → $RUN_DIR/load/load-summary-${MODE}.csv"
