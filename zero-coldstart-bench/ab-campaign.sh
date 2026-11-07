#!/usr/bin/env bash
# Usage : ./ab-campaign.sh <service> <chemin-GET> [repetitions=5] [requetes_par_variante=1000] [warmup=200]
set -euo pipefail
SVC=$1; TEST_PATH=$2; REPS=${3:-5}; N=${4:-1000}; WARM=${5:-200}
API=http://localhost:8090/api/abtest
OUT=$HOME/benchmark-results/campagne/ab/$SVC
mkdir -p "$OUT"; rm -f "$OUT"/run_*.json

curl -s -X POST "$API/setup/$SVC" > /dev/null
kubectl rollout status deployment/$SVC-ab-jvm -n library --timeout=180s
kubectl rollout status deployment/$SVC-ab-native -n library --timeout=180s
sleep 20

# Controle prealable : les deux variantes doivent repondre 200 sur le chemin teste
PORT=$(kubectl get svc "$SVC" -n library -o jsonpath='{.spec.ports[0].port}')
check_variant() {
  kubectl port-forward -n library svc/$SVC-ab-$1 $2:$PORT > /dev/null 2>&1 & local PF=$!
  sleep 3
  local code; code=$(curl -s -o /dev/null -w "%{http_code}" "localhost:$2$TEST_PATH" || true)
  kill $PF 2>/dev/null || true; wait $PF 2>/dev/null || true
  echo "controle $1 : HTTP $code"
  [ "$code" = "200" ]
}
if ! { check_variant jvm 19901 && check_variant native 19902; }; then
  echo "ABANDON : le chemin $TEST_PATH ne repond pas 200 sur les deux variantes."
  curl -s -X DELETE "$API/teardown/$SVC" > /dev/null
  exit 1
fi

for r in $(seq 1 "$REPS"); do
  curl -s -X DELETE "$API/results/$SVC" > /dev/null
  curl -s -X POST "$API/run" -H 'Content-Type: application/json' \
    -d "{\"serviceName\":\"$SVC\",\"totalRequests\":$((N*2)),\"jvmSplitPercent\":50,\"testPath\":\"$TEST_PATH\",\"warmupRequests\":$WARM}" > /dev/null
  sleep 5
  while curl -s "$API/status/$SVC" | grep -q '"running":true'; do sleep 5; done
  curl -s "$API/results/$SVC" > "$OUT/run_$r.json"
  echo "run $r/$REPS termine"
done
curl -s -X DELETE "$API/teardown/$SVC" > /dev/null
python3 "$(dirname "$0")/ab-aggregate.py" "$OUT"
