#!/bin/bash
RESULTS_DIR="$HOME/benchmark-results"
RUN_DIR=$(ls -t "$RESULTS_DIR" | head -1)
RUN_DIR="$RESULTS_DIR/$RUN_DIR"
MODE="jvm"
LOAD_DURATION=60
LOAD_THREADS=20
LOAD_RAMP=10

mkdir -p "$RUN_DIR/load"
rm -f "$RUN_DIR/load/results-${MODE}.jtl"

cat > "$RUN_DIR/load/test-plan.jmx" << 'JMXEOF'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Library API Benchmark" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments"/>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="GET books" enabled="true">
        <intProp name="ThreadGroup.num_threads">20</intProp>
        <intProp name="ThreadGroup.ramp_time">10</intProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">60</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">true</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api/books" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.path">/api/books</stringProp>
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

echo "Lancement JMeter — $LOAD_THREADS threads · ${LOAD_DURATION}s"
jmeter -n \
  -t "$RUN_DIR/load/test-plan.jmx" \
  -l "$RUN_DIR/load/results-${MODE}.jtl" \
  -Jsummariser.interval=10 \
  2>&1 | grep -E "summary|ERROR|error"

echo ""
echo "Extraction des métriques..."
TOTAL=$(tail -n +2 "$RUN_DIR/load/results-${MODE}.jtl" | wc -l)
AVG=$(tail -n +2 "$RUN_DIR/load/results-${MODE}.jtl" | awk -F',' '{sum+=$2; count++} END {printf "%.0f", sum/count}')
THROUGHPUT=$(echo "scale=2; $TOTAL / 60" | bc)
echo "Total requêtes  : $TOTAL"
echo "Latence moyenne : ${AVG} ms"
echo "Throughput      : ${THROUGHPUT} req/s"
echo "Terminé — résultats dans $RUN_DIR/load/"
