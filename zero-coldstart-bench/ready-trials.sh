#!/usr/bin/env bash
# Temps jusqu'a Ready avec des sondes IDENTIQUES et rapides pour JVM et natif.
set -uo pipefail
SVCS="catalog-service user-service loan-service library-gateway"
TRIALS=${1:-10}
OUT=$HOME/benchmark-results/campagne/ready-sondes-rapides.csv
API=http://localhost:8090/api/cluster/deploy
echo "mode,service,trial,ready_s" > "$OUT"

probe() { # $1=service $2=startup_delay $3=startup_period $4=startup_failures $5=readiness_period
  kubectl patch deploy "$1" -n library --type=json -p="[
   {\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/startupProbe/initialDelaySeconds\",\"value\":$2},
   {\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/startupProbe/periodSeconds\",\"value\":$3},
   {\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/startupProbe/failureThreshold\",\"value\":$4},
   {\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/readinessProbe/periodSeconds\",\"value\":$5}]" > /dev/null
}
restore() {
  for s in $SVCS; do if [ "$s" = library-gateway ]; then probe $s 15 5 12 10; else probe $s 30 10 12 10; fi; done
  for s in $SVCS; do kubectl rollout status deploy/$s -n library --timeout=300s > /dev/null; done
  echo "=== sondes apres restauration (attendu : 30/10 ; gateway 15/5 ; readiness 10) ==="
  for s in $SVCS; do kubectl describe pod -l app=$s -n library | grep -E "Startup:|Readiness:" | sed "s/^ */$s : /"; done
}
trap restore EXIT

for s in $SVCS; do probe $s 0 1 120 1; done
for s in $SVCS; do kubectl rollout status deploy/$s -n library --timeout=300s > /dev/null; done

for MODE in JVM NATIVE; do
  for s in $SVCS; do curl -s -X POST $API -H 'Content-Type: application/json' -d "{\"serviceName\":\"$s\",\"mode\":\"$MODE\"}" > /dev/null; done
  for s in $SVCS; do kubectl rollout status deploy/$s -n library --timeout=300s > /dev/null; done
  for s in $SVCS; do
    for n in $(seq 1 $TRIALS); do
      kubectl scale deploy $s -n library --replicas=0 > /dev/null
      kubectl wait --for=delete pod -l app=$s -n library --timeout=60s > /dev/null 2>&1 || true
      sleep 2
      T0=$(date +%s.%N)
      kubectl scale deploy $s -n library --replicas=1 > /dev/null
      kubectl rollout status deploy/$s -n library --timeout=180s > /dev/null
      T1=$(date +%s.%N)
      echo "$MODE,$s,$n,$(awk "BEGIN{printf \"%.2f\", $T1-$T0}")" | tee -a "$OUT"
      sleep 3
    done
  done
done
