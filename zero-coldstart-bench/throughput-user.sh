#!/usr/bin/env bash
# Debit en regime etabli : wrk (open source), 10 connexions, 30 s de chauffe puis 60 s mesurees, 3 repetitions.
set -uo pipefail
OUT=$HOME/benchmark-results/campagne/debit-wrk-user.csv
echo "service,variante,rep,req_par_s,p50_ms,p99_ms,erreurs" > "$OUT"
run() { # service chemin
  local SVC=$1 TPATH=$2
  curl -s -X POST localhost:8090/api/abtest/setup/$SVC > /dev/null
  for v in jvm native; do kubectl rollout status deploy/$SVC-ab-$v -n library --timeout=180s > /dev/null; done
  sleep 20
  local PORT; PORT=$(kubectl get svc $SVC -n library -o jsonpath='{.spec.ports[0].port}')
  local i=0
  for v in jvm native; do
    i=$((i+1)); local LP=$((19600+i))
    kubectl port-forward -n library svc/$SVC-ab-$v $LP:$PORT > /dev/null 2>&1 & local PF=$!
    sleep 3
    wrk -t2 -c10 -d120s "http://localhost:$LP$TPATH" > /dev/null 2>&1       # chauffe
    for rep in 1 2 3 4 5; do
      R=$(wrk -t2 -c10 -d60s --latency "http://localhost:$LP$TPATH")
      RPS=$(echo "$R" | awk '/Requests\/sec/{print $2}')
      P50=$(echo "$R" | awk '/ 50%/{print $2}'); P99=$(echo "$R" | awk '/ 99%/{print $2}')
      ERR=$(echo "$R" | awk '/Non-2xx/{print $NF}'); echo "$SVC,$v,$rep,$RPS,${P50:-},${P99:-},${ERR:-0}" | tee -a "$OUT"
    done
    kill $PF 2>/dev/null; wait $PF 2>/dev/null
  done
  curl -s -X DELETE localhost:8090/api/abtest/teardown/$SVC > /dev/null
}
run user-service    "/users/paginated?page=0&size=20"
