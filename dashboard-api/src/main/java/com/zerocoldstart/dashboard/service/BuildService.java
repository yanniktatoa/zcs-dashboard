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
public class BuildService {

    private final ProjectProperties projectProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<String, Sinks.Many<SseEvent>> buildSinks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runningBuilds = new ConcurrentHashMap<>();

    public Flux<SseEvent> streamBuildLogs(String serviceName) {
        Sinks.Many<SseEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        buildSinks.put(serviceName, sink);
        return sink.asFlux();
    }

    public void startJvmBuild(String serviceName) {
        if (Boolean.TRUE.equals(runningBuilds.get(serviceName))) {
            log.warn("Build already running for {}", serviceName);
            return;
        }
        runningBuilds.put(serviceName, true);
        executor.submit(() -> {
            try {
                String serviceDir = projectProperties.getBasePath() + "/" + serviceName;

                emit(serviceName, SseEvent.builder().type("LOG")
                        .message("Demarrage du build JVM pour " + serviceName)
                        .progress(0).timestamp(System.currentTimeMillis()).build());

                emit(serviceName, SseEvent.builder().type("LOG")
                        .message("Compilation Maven (mvn clean package)...")
                        .progress(10).timestamp(System.currentTimeMillis()).build());

                String mvnCmd = String.format("cd %s && mvn clean package -DskipTests", serviceDir);
                boolean buildSuccess = runCommand(serviceName,
                        new String[]{"bash", "-c", mvnCmd}, 10, 70);

                if (!buildSuccess) {
                    emit(serviceName, SseEvent.builder().type("ERROR")
                            .message("Echec de la compilation Maven")
                            .progress(0).timestamp(System.currentTimeMillis()).build());
                    return;
                }

                emit(serviceName, SseEvent.builder().type("LOG")
                        .message("Construction de l image Docker JVM...")
                        .progress(72).timestamp(System.currentTimeMillis()).build());

                String dockerCmd = String.format(
                        "cd %s && docker build -t %s:jvm .", serviceDir, serviceName);
                runCommand(serviceName, new String[]{"bash", "-c", dockerCmd}, 72, 85);

                emit(serviceName, SseEvent.builder().type("LOG")
                        .message("Import de l image dans k3d...")
                        .progress(86).timestamp(System.currentTimeMillis()).build());

                runCommand(serviceName, new String[]{"bash", "-c",
                        "k3d image import " + serviceName + ":jvm -c zero-coldstart"}, 86, 95);

                runCommand(serviceName, new String[]{"bash", "-c",
                        "kubectl rollout restart deployment/" + serviceName + " -n library"
                }, 95, 98);

                emit(serviceName, SseEvent.builder().type("SUCCESS")
                        .message("Build JVM de " + serviceName + " termine !")
                        .progress(100).timestamp(System.currentTimeMillis()).build());

            } catch (Exception e) {
                log.error("JVM Build failed for {}", serviceName, e);
                emit(serviceName, SseEvent.builder().type("ERROR")
                        .message("Erreur : " + e.getMessage())
                        .progress(0).timestamp(System.currentTimeMillis()).build());
            } finally {
                runningBuilds.put(serviceName, false);
                Sinks.Many<SseEvent> sink = buildSinks.get(serviceName);
                if (sink != null) sink.tryEmitComplete();
            }
        });
    }

    public void startNativeBuild(String serviceName) {
        if (Boolean.TRUE.equals(runningBuilds.get(serviceName))) {
            log.warn("Build already running for {}", serviceName);
            return;
        }

        runningBuilds.put(serviceName, true);
        executor.submit(() -> {
            try {
                String serviceDir = projectProperties.getBasePath() + "/" + serviceName;
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Demarrage du build natif pour " + serviceName)
                        .progress(0)
                        .timestamp(System.currentTimeMillis())
                        .build());

                // Scale down autres pods pour liberer RAM
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Scale down des pods pour liberer la memoire...")
                        .progress(5)
                        .timestamp(System.currentTimeMillis())
                        .build());

                runCommand(serviceName, new String[]{
                        "bash", "-c",
                        "kubectl scale deployment catalog-service user-service loan-service library-gateway -n library --replicas=0 2>/dev/null || true"
                }, 5, 10);

                // Build Maven natif
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Lancement de la compilation GraalVM Native Image...")
                        .progress(10)
                        .timestamp(System.currentTimeMillis())
                        .build());

                String mvnCmd = String.format(
                        "cd %s && MAVEN_OPTS=\"-Xmx2g\" mvn -Pnative package -DskipTests " +
                        "-Dnative.image.buildArgs=\"--gc=G1 -J-Xmx8g -J-XX:ActiveProcessorCount=4 -H:DeadlockWatchdogInterval=60\"",
                        serviceDir
                );

                boolean buildSuccess = runCommand(serviceName, new String[]{"bash", "-c", mvnCmd}, 10, 70);

                if (!buildSuccess) {
                    emit(serviceName, SseEvent.builder()
                            .type("ERROR")
                            .message("Echec de la compilation native")
                            .progress(0)
                            .timestamp(System.currentTimeMillis())
                            .build());
                    return;
                }

                // Docker build
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Construction de l'image Docker native...")
                        .progress(72)
                        .timestamp(System.currentTimeMillis())
                        .build());

                String dockerCmd = String.format(
                        "cd %s && docker build -f Dockerfile.native -t %s:native .",
                        serviceDir, serviceName
                );
                runCommand(serviceName, new String[]{"bash", "-c", dockerCmd}, 72, 82);

                // k3d import
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Import de l'image dans k3d...")
                        .progress(83)
                        .timestamp(System.currentTimeMillis())
                        .build());

                runCommand(serviceName, new String[]{
                        "bash", "-c",
                        "k3d image import " + serviceName + ":native -c zero-coldstart"
                }, 83, 90);

                // Redeploy
                emit(serviceName, SseEvent.builder()
                        .type("LOG")
                        .message("Redeploiement dans Kubernetes...")
                        .progress(91)
                        .timestamp(System.currentTimeMillis())
                        .build());

                runCommand(serviceName, new String[]{
                        "bash", "-c",
                        "kubectl rollout restart deployment/" + serviceName + " -n library"
                }, 91, 95);

                // Rescale autres pods
                runCommand(serviceName, new String[]{
                        "bash", "-c",
                        "kubectl scale deployment catalog-service user-service loan-service library-gateway -n library --replicas=1 2>/dev/null || true"
                }, 95, 98);

                emit(serviceName, SseEvent.builder()
                        .type("SUCCESS")
                        .message("Build natif de " + serviceName + " termine avec succes !")
                        .progress(100)
                        .timestamp(System.currentTimeMillis())
                        .build());

            } catch (Exception e) {
                log.error("Build failed for {}", serviceName, e);
                emit(serviceName, SseEvent.builder()
                        .type("ERROR")
                        .message("Erreur inattendue : " + e.getMessage())
                        .progress(0)
                        .timestamp(System.currentTimeMillis())
                        .build());
            } finally {
                runningBuilds.put(serviceName, false);
                Sinks.Many<SseEvent> sink = buildSinks.get(serviceName);
                if (sink != null) sink.tryEmitComplete();
            }
        });
    }

    public boolean isBuildRunning(String serviceName) {
        return Boolean.TRUE.equals(runningBuilds.get(serviceName));
    }

    public void cancelBuild(String serviceName) {
        log.info("Cancelling build for {}", serviceName);
        runningBuilds.put(serviceName, false);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "pkill -f 'mvn.*" + serviceName + "' || true");
            pb.start().waitFor();
        } catch (Exception e) {
            log.warn("Could not kill maven process: {}", e.getMessage());
        }
        Sinks.Many<SseEvent> sink = buildSinks.get(serviceName);
        if (sink != null) {
            sink.tryEmitNext(SseEvent.builder()
                    .type("ERROR")
                    .message("Build annule par l'utilisateur")
                    .progress(0)
                    .timestamp(System.currentTimeMillis())
                    .build());
            sink.tryEmitComplete();
        }
    }

    private boolean runCommand(String serviceName, String[] command,
                                int progressStart, int progressEnd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            int[] lineCount = {0};
            while ((line = reader.readLine()) != null) {
                lineCount[0]++;
                int progress = Math.min(progressStart +
                        (lineCount[0] % Math.max(1, progressEnd - progressStart)),
                        progressEnd - 1);

                // Filtrer les lignes GraalVM importantes
                if (line.contains("[") && line.contains("/8]") ||
                    line.contains("BUILD") || line.contains("ERROR") ||
                    line.contains("WARNING") || line.contains("Generating") ||
                    line.contains("Finished") || line.contains("reachable")) {
                    emit(serviceName, SseEvent.builder()
                            .type("LOG")
                            .message(line)
                            .progress(progress)
                            .timestamp(System.currentTimeMillis())
                            .build());
                }
            }
        }

        int exitCode = process.waitFor();
        return exitCode == 0;
    }

    private void emit(String serviceName, SseEvent event) {
        Sinks.Many<SseEvent> sink = buildSinks.get(serviceName);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }
}
