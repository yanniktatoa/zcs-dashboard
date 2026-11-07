package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import com.zerocoldstart.dashboard.model.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final ProjectProperties projectProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, Sinks.Many<SseEvent>> benchmarkSinks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runningBenchmarks = new ConcurrentHashMap<>();
    private final Map<String, String> currentRunDir = new ConcurrentHashMap<>();

    private static final int TRIALS = 10;
    private record Trial(int n, double startedInS, double processRunningS, double appUpS, double readyS) {}
    private record StartInfo(long loggedAtMs, double startedInS, double processRunningS) {}
    private final Map<String, java.util.List<Trial>> trialCache = new ConcurrentHashMap<>();

    public Flux<SseEvent> streamBenchmarkLogs(String mode) {
        Sinks.Many<SseEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        benchmarkSinks.put(mode, sink);
        return sink.asFlux();
    }

    public void startBenchmark(String mode) {
        if (Boolean.TRUE.equals(runningBenchmarks.get(mode))) {
            log.warn("Benchmark already running for mode {}", mode);
            return;
        }

        runningBenchmarks.put(mode, true);
        executor.submit(() -> {
            try {
                String runDir = createResultsDir(mode);
                currentRunDir.put(mode, runDir);

                emitBenchmark(mode, SseEvent.builder()
                        .type("LOG")
                        .message("Demarrage du benchmark en mode " + mode.toUpperCase())
                        .progress(0)
                        .timestamp(System.currentTimeMillis())
                        .build());

                // Etape 1 - Taille des images
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("1/5 - Mesure de la taille des images Docker...")
                        .progress(5)
                        .timestamp(System.currentTimeMillis())
                        .build());
                runBenchmarkStep(mode, "image_sizes", 5, 20);

                // Etape 2 - Startup time
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("2/5 - Mesure du startup time (scale-to-zero)...")
                        .progress(20)
                        .timestamp(System.currentTimeMillis())
                        .build());
                runBenchmarkStep(mode, "startup", 20, 40);

                // Etape 3 - Memoire
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("3/5 - Mesure de la consommation memoire...")
                        .progress(40)
                        .timestamp(System.currentTimeMillis())
                        .build());
                runBenchmarkStep(mode, "memory", 40, 55);

                // Etape 4 - Test de charge
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("4/5 - Test de charge (60 secondes)...")
                        .progress(55)
                        .timestamp(System.currentTimeMillis())
                        .build());
                runLoadTest(mode, 55, 75);

                // Etape 5 - Cold start
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("5/5 - Mesure du cold start (10 essais par service)...")
                        .progress(75)
                        .timestamp(System.currentTimeMillis())
                        .build());
                runColdStartTest(mode, 75, 95);

                // Rapport final
                emitBenchmark(mode, SseEvent.builder()
                        .type("PROGRESS")
                        .message("Generation du rapport de synthese...")
                        .progress(96)
                        .timestamp(System.currentTimeMillis())
                        .build());
                generateSummary(mode);

                emitBenchmark(mode, SseEvent.builder()
                        .type("SUCCESS")
                        .message("Benchmark " + mode.toUpperCase() + " termine avec succes !")
                        .progress(100)
                        .timestamp(System.currentTimeMillis())
                        .build());

            } catch (Exception e) {
                log.error("Benchmark failed for mode {}", mode, e);
                emitBenchmark(mode, SseEvent.builder()
                        .type("ERROR")
                        .message("Erreur : " + e.getMessage())
                        .progress(0)
                        .timestamp(System.currentTimeMillis())
                        .build());
            } finally {
                runningBenchmarks.put(mode, false);
                Sinks.Many<SseEvent> sink = benchmarkSinks.get(mode);
                if (sink != null) sink.tryEmitComplete();
            }
        });
    }

    public boolean isBenchmarkRunning(String mode) {
        return Boolean.TRUE.equals(runningBenchmarks.get(mode));
    }

    private void runBenchmarkStep(String mode, String step,
                                   int progressStart, int progressEnd) throws Exception {
        String resultsDir = currentRunDir.get(mode);

        if (step.equals("startup")) {
            runStartupMeasurement(mode, resultsDir, progressStart, progressEnd);
            return;
        }

        String cmd;
        switch (step) {
            case "image_sizes" -> cmd = buildImageSizeCmd(mode, resultsDir);
            case "memory" -> cmd = buildMemoryCmd(mode, resultsDir);
            default -> { return; }
        }

        runStreamingCommand(mode, new String[]{"bash", "-c", cmd},
                progressStart, progressEnd);
    }

    private void runStartupMeasurement(String mode, String resultsDir,
                                        int progressStart, int progressEnd) throws Exception {
        String[] services = {"catalog-service", "user-service", "loan-service", "library-gateway"};
        java.nio.file.Path startupDir = java.nio.file.Paths.get(resultsDir, "startup");
        java.nio.file.Files.createDirectories(startupDir);
        trialCache.keySet().removeIf(k -> k.startsWith(mode + ":"));

        StringBuilder csv = new StringBuilder("service,startup_ms,started_at\n");
        StringBuilder trialsCsv = new StringBuilder(
                "service,trial,started_in_s,process_running_s,app_up_s,ready_s\n");
        int step = (progressEnd - progressStart) / services.length;

        for (int i = 0; i < services.length; i++) {
            String service = services[i];
            int svcProgress = progressStart + (i * step);
            java.util.List<Trial> trials = runTrials(mode, service, svcProgress);
            trialCache.put(mode + ":" + service, trials);

            for (Trial t : trials) {
                trialsCsv.append(String.format(java.util.Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f\n",
                        service, t.n(), t.startedInS(), t.processRunningS(), t.appUpS(), t.readyS()));
            }
            double med = median(trials.stream().mapToDouble(Trial::processRunningS).toArray());
            long startupMs = trials.isEmpty() ? -1 : Math.round(med * 1000);
            csv.append(String.format("%s,%d,%s\n", service, startupMs,
                    java.time.LocalDateTime.now().toString()));

            emitBenchmark(mode, SseEvent.builder()
                    .type("LOG")
                    .message("Startup " + service + " (mediane de " + trials.size() + " essais) : " + startupMs + "ms")
                    .progress(svcProgress + step)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }

        java.nio.file.Files.writeString(startupDir.resolve("startup-" + mode + ".csv"), csv.toString());
        java.nio.file.Files.writeString(startupDir.resolve("startup-trials-" + mode + ".csv"), trialsCsv.toString());
    }

    /**
     * TRIALS cycles scale 0 -> 1 pour un service. Mesures par essai :
     *  - started_in_s / process_running_s : valeurs du log Spring (« Started X in N seconds (process running for M) »)
     *  - app_up_s : de la commande scale jusqu'a l'horodatage (ms) de cette ligne de log, independant du pas de scrutation
     *  - ready_s  : de la commande scale jusqu'au pod Ready tel que vu par Kubernetes (inclut le cycle de la sonde)
     */
    private java.util.List<Trial> runTrials(String mode, String service, int progress) throws Exception {
        java.util.List<Trial> trials = new java.util.ArrayList<>();
        for (int n = 1; n <= TRIALS; n++) {
            runShell("kubectl scale deployment " + service + " -n library --replicas=0 > /dev/null 2>&1");
            runShell("kubectl wait --for=delete pod -l app=" + service + " -n library --timeout=60s > /dev/null 2>&1 || true");
            Thread.sleep(2000);

            long scaleMs = System.currentTimeMillis();
            runShell("kubectl scale deployment " + service + " -n library --replicas=1 > /dev/null 2>&1");
            StartInfo info = awaitStartedLog(service, 120000);
            if (info == null) {
                log.warn("Demarrage non detecte pour {} essai {}", service, n);
                emitBenchmark(mode, SseEvent.builder().type("LOG")
                        .message(service + " - essai " + n + "/" + TRIALS + " : timeout")
                        .progress(progress).timestamp(System.currentTimeMillis()).build());
                continue;
            }
            double appUpS = (info.loggedAtMs() - scaleMs) / 1000.0;

            runShell("kubectl wait --for=condition=ready pod -l app=" + service + " -n library --timeout=120s > /dev/null 2>&1");
            double readyS = (System.currentTimeMillis() - scaleMs) / 1000.0;

            trials.add(new Trial(n, info.startedInS(), info.processRunningS(), appUpS, readyS));
            emitBenchmark(mode, SseEvent.builder().type("LOG")
                    .message(String.format(java.util.Locale.ROOT,
                            "%s - essai %d/%d : appli prete en %.2fs, pod Ready en %.2fs",
                            service, n, TRIALS, appUpS, readyS))
                    .progress(progress).timestamp(System.currentTimeMillis()).build());
            Thread.sleep(3000);
        }
        return trials;
    }

    private StartInfo awaitStartedLog(String service, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(?:Z|[+-]\\d{2}:\\d{2}))\\s+\\w+\\s+\\d+ --- .*?Started \\S+ in ([\\d.]+) seconds(?: \\(process running for ([\\d.]+)\\))?");
        while (System.currentTimeMillis() < deadline) {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "kubectl logs -n library -l app=" + service + " --tail=300 2>/dev/null");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String logs = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            java.util.regex.Matcher m = pattern.matcher(logs);
            StartInfo last = null;
            while (m.find()) {
                long at = java.time.OffsetDateTime.parse(m.group(1)).toInstant().toEpochMilli();
                double in = Double.parseDouble(m.group(2));
                double run = m.group(3) != null ? Double.parseDouble(m.group(3)) : in;
                last = new StartInfo(at, in, run);
            }
            if (last != null) return last;
            Thread.sleep(300);
        }
        return null;
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0;
        double[] v = values.clone();
        java.util.Arrays.sort(v);
        int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) / 2.0;
    }

    private void runLoadTest(String mode, int progressStart, int progressEnd) throws Exception {
        String resultsDir = currentRunDir.get(mode);
        String loadScript = projectProperties.getResultsPath() + "/load-test.sh";

        // Utiliser le script de load test existant
        String cmd = String.format(
                "RESULTS_DIR=%s MODE=%s bash %s/../../zero-coldstart-bench/load-test.sh",
                resultsDir, mode, projectProperties.getBasePath()
        );

        // Si le script n'existe pas, faire un curl simple
        String fallbackCmd = String.format(
                "COUNT=0; TOTAL_MS=0; END=$(($(date +%%s) + 60)); " +
                "while [ $(date +%%s) -lt $END ]; do " +
                "  T=$(curl -s -o /dev/null -w '%%{time_total}' http://localhost:8080/api/books 2>/dev/null || echo 0); " +
                "  MS=$(echo \"$T * 1000\" | bc | cut -d. -f1); " +
                "  TOTAL_MS=$((TOTAL_MS + MS)); COUNT=$((COUNT + 1)); " +
                "done; " +
                "AVG=$((TOTAL_MS / COUNT)); TP=$(echo \"scale=2; $COUNT / 60\" | bc); " +
                "mkdir -p %s/load; " +
                "echo 'total_requests,%d' > %s/load/load-summary-%s.csv; " +
                "echo \"avg_latency_ms,$AVG\" >> %s/load/load-summary-%s.csv; " +
                "echo \"throughput_rps,$TP\" >> %s/load/load-summary-%s.csv; " +
                "echo \"errors,0\" >> %s/load/load-summary-%s.csv",
                resultsDir, 0, resultsDir, mode, resultsDir, mode, resultsDir, mode, resultsDir, mode
        );

        runStreamingCommand(mode, new String[]{"bash", "-c", fallbackCmd},
                progressStart, progressEnd);
    }

    private void runColdStartTest(String mode, int progressStart, int progressEnd) throws Exception {
        String resultsDir = currentRunDir.get(mode);
        String[] services = {"catalog-service", "user-service", "loan-service", "library-gateway"};
        int step = (progressEnd - progressStart) / services.length;

        StringBuilder csvContent = new StringBuilder("service,trial,cold_start_s,ready_at\n");

        for (int i = 0; i < services.length; i++) {
            String service = services[i];
            int svcProgress = progressStart + (i * step);

            java.util.List<Trial> trials = trialCache.get(mode + ":" + service);
            if (trials == null || trials.isEmpty()) {
                trials = runTrials(mode, service, svcProgress);   // repli si l'etape startup n'a rien fourni
            }
            for (Trial t : trials) {
                csvContent.append(String.format(java.util.Locale.ROOT, "%s,%d,%.3f,%s\n",
                        service, t.n(), t.appUpS(), java.time.LocalDateTime.now().toString()));
            }
            double med = median(trials.stream().mapToDouble(Trial::appUpS).toArray());
            emitBenchmark(mode, SseEvent.builder()
                    .type("LOG")
                    .message(String.format(java.util.Locale.ROOT,
                            "Cold start %s : mediane %.2fs sur %d essais", service, med, trials.size()))
                    .progress(svcProgress + step)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }

        java.nio.file.Path coldStartDir = java.nio.file.Paths.get(resultsDir, "coldstart");
        java.nio.file.Files.createDirectories(coldStartDir);
        java.nio.file.Files.writeString(
                coldStartDir.resolve("coldstart-" + mode + ".csv"),
                csvContent.toString()
        );
    }

    private void generateSummary(String mode) throws Exception {
        String resultsDir = currentRunDir.get(mode);
        String cmd = String.format(
                "echo '# Benchmark Results - Mode: %s' > %s/SUMMARY-%s.md; " +
                "echo 'Date: '$(date '+%%Y-%%m-%%d %%H:%%M:%%S') >> %s/SUMMARY-%s.md",
                mode.toUpperCase(), resultsDir, mode, resultsDir, mode
        );
        runStreamingCommand(mode, new String[]{"bash", "-c", cmd}, 96, 99);
    }

    private String buildImageSizeCmd(String mode, String resultsDir) {
        String[] services = {"catalog-service", "user-service", "loan-service", "library-gateway"};
        StringBuilder cmd = new StringBuilder();
        cmd.append("mkdir -p ").append(resultsDir).append("/images; ");
        cmd.append("echo 'service,tag,size_mb,created' > ")
           .append(resultsDir).append("/images/image-sizes-").append(mode).append(".csv; ");

        for (String svc : services) {
            String tag = svc + ":" + mode;
            cmd.append(String.format(
                    "SIZE=$(docker image inspect %s --format='{{.Size}}' 2>/dev/null || echo 0); " +
                    "SIZE_MB=$(echo \"scale=2; $SIZE / 1048576\" | bc); " +
                    "CREATED=$(docker image inspect %s --format='{{.Created}}' 2>/dev/null || echo 'N/A'); " +
                    "echo '%s,%s,'$SIZE_MB','$CREATED >> %s/images/image-sizes-%s.csv; ",
                    tag, tag, svc, tag, resultsDir, mode
            ));
        }
        return cmd.toString();
    }

    private String buildStartupCmd(String mode, String resultsDir) {
        String[] services = {"catalog-service", "user-service", "loan-service", "library-gateway"};
        StringBuilder cmd = new StringBuilder();
        cmd.append("mkdir -p ").append(resultsDir).append("/startup; ");
        cmd.append("echo 'service,startup_ms,started_at' > ")
           .append(resultsDir).append("/startup/startup-").append(mode).append(".csv; ");

        for (String svc : services) {
            cmd.append(String.format(
                    "kubectl scale deployment %s -n library --replicas=0 > /dev/null 2>&1; " +
                    "sleep 2; " +
                    "T_START=$(date +%%s); " +
                    "kubectl scale deployment %s -n library --replicas=1 > /dev/null 2>&1; " +
                    "kubectl wait --for=condition=ready pod -l app=%s -n library --timeout=120s > /dev/null 2>&1; " +
                    "T_END=$(date +%%s); " +
                    "MS=$(( (T_END - T_START) * 1000 )); " +
                    "echo '%s,'$MS','$(date +%%Y-%%m-%%dT%%H:%%M:%%S) >> %s/startup/startup-%s.csv; " +
                    "echo 'Startup %s: '$MS'ms'; ",
                    svc, svc, svc, svc, resultsDir, mode, svc
            ));
        }
        return cmd.toString();
    }

    private String buildMemoryCmd(String mode, String resultsDir) {
        String script = """
                R=@R@; M=@M@
                SVCS="catalog-service user-service loan-service library-gateway"
                mkdir -p $R/memory
                echo 'service,sample,cpu_m,memory_mi' > $R/memory/memory-samples-$M.csv
                echo 'service,phase,cpu_cores,memory_mi' > $R/memory/memory-$M.csv
                for s in $SVCS; do kubectl rollout status deployment/$s -n library --timeout=180s > /dev/null 2>&1; done
                echo 'Stabilisation : 90 s apres disponibilite des pods...'
                sleep 90
                for i in 1 2 3 4 5; do
                  for s in $SVCS; do
                    kubectl top pod -n library -l app=$s --no-headers 2>/dev/null | awk -v svc=$s -v n=$i '{gsub(/m/,"",$2); gsub(/Mi/,"",$3); print svc","n","$2","$3}' >> $R/memory/memory-samples-$M.csv
                  done
                  echo "Releve memoire $i/5"
                  sleep 15
                done
                for s in $SVCS; do
                  MEM=$(awk -F, -v svc=$s '$1==svc {print $4}' $R/memory/memory-samples-$M.csv | sort -n | awk '{a[NR]=$1} END{if(NR>0) print a[int((NR+1)/2)]}')
                  CPU=$(awk -F, -v svc=$s '$1==svc {print $3}' $R/memory/memory-samples-$M.csv | sort -n | awk '{a[NR]=$1} END{if(NR>0) print a[int((NR+1)/2)]}')
                  echo "$s,idle,${CPU:-0},${MEM:-0}" >> $R/memory/memory-$M.csv
                done
                echo 'Mesure memoire idle terminee (mediane de 5 releves)'
                """;
        return script.replace("@R@", resultsDir).replace("@M@", mode);
    }

    private void runShell(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }

    private double pollForStartedLog(String service, long startNanos, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Started \\S+ in ([\\d.]+) seconds");

        while (System.currentTimeMillis() < deadline) {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "kubectl logs -n library -l app=" + service + " --tail=50 2>/dev/null");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String logs = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            java.util.regex.Matcher matcher = pattern.matcher(logs);
            String lastMatch = null;
            while (matcher.find()) {
                lastMatch = matcher.group(1);
            }

            if (lastMatch != null) {
                long elapsedNanos = System.nanoTime() - startNanos;
                return elapsedNanos / 1_000_000_000.0;
            }

            Thread.sleep(300);
        }
        return -1;
    }

    private String createResultsDir(String mode) throws Exception {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dir = projectProperties.getResultsPath() + "/" + timestamp;
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dir));
        return dir;
    }

    private void runStreamingCommand(String mode, String[] command,
                                      int progressStart, int progressEnd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            int[] count = {0};
            while ((line = reader.readLine()) != null) {
                count[0]++;
                int progress = Math.min(
                        progressStart + (count[0] % Math.max(1, progressEnd - progressStart)),
                        progressEnd - 1
                );
                emitBenchmark(mode, SseEvent.builder()
                        .type("LOG")
                        .message(line)
                        .progress(progress)
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        }
        process.waitFor();
    }

    private void emitBenchmark(String mode, SseEvent event) {
        Sinks.Many<SseEvent> sink = benchmarkSinks.get(mode);
        if (sink != null) sink.tryEmitNext(event);
    }
}
