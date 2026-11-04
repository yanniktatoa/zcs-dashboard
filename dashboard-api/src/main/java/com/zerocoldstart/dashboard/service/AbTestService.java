package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import com.zerocoldstart.dashboard.model.AbTestRequest;
import com.zerocoldstart.dashboard.model.AbTestResult;
import com.zerocoldstart.dashboard.model.AbTestStats;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbTestService {

    private final KubernetesClient client;
    private final KubernetesProperties kubernetesProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, Sinks.Many<AbTestResult>> testSinks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> runningTests = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> forwardPorts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<AbTestResult>> testResults = new java.util.concurrent.ConcurrentHashMap<>();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Flux<AbTestResult> streamResults(String serviceName) {
        Sinks.Many<AbTestResult> sink = Sinks.many().multicast().onBackpressureBuffer();
        testSinks.put(serviceName, sink);
        return sink.asFlux();
    }

    public boolean isRunning(String serviceName) {
        return Boolean.TRUE.equals(runningTests.get(serviceName));
    }

    /**
     * Deploie les deux variantes A/B (jvm et native) du service avec 1 replica chacune,
     * en parallele du deployment principal existant.
     */
    public void setupAbDeployments(String serviceName) {
        var svcConfig = kubernetesProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Service inconnu: " + serviceName));

        deployVariant(serviceName, "jvm", svcConfig.getJvmImage(), svcConfig.getPort());
        deployVariant(serviceName, "native", svcConfig.getNativeImage(), svcConfig.getPort());
    }

    private void deployVariant(String serviceName, String variant, String image, int port) {
        String name = serviceName + "-ab-" + variant;
        String namespace = kubernetesProperties.getNamespace();

        Map<String, String> labels = Map.of("app", name, "ab-variant", variant, "ab-base", serviceName);

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector().withMatchLabels(labels).endSelector()
                .withNewTemplate()
                .withNewMetadata().withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(image)
                .addNewPort().withContainerPort(port).endPort()
                .withEnvFrom(
                        new EnvFromSourceBuilder().withNewConfigMapRef(
                                serviceName.replace("-service", "").replace("library-", "") + "-config", false
                        ).build()
                )
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        boolean exists = client.apps().deployments().inNamespace(namespace).withName(name).get() != null;
        if (exists) {
            client.apps().deployments().inNamespace(namespace).withName(name)
                    .edit(d -> {
                        d.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
                        return d;
                    });
        } else {
            client.apps().deployments().inNamespace(namespace).resource(deployment).create();
        }

        io.fabric8.kubernetes.api.model.Service svc = new ServiceBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withSelector(labels)
                .addNewPort().withPort(port).withTargetPort(new IntOrString(port)).endPort()
                .endSpec()
                .build();

        boolean svcExists = client.services().inNamespace(namespace).withName(name).get() != null;
        if (!svcExists) {
            client.services().inNamespace(namespace).resource(svc).create();
        }
    }

    public void teardownAbDeployments(String serviceName) {
        String namespace = kubernetesProperties.getNamespace();
        for (String variant : List.of("jvm", "native")) {
            String name = serviceName + "-ab-" + variant;
            client.apps().deployments().inNamespace(namespace).withName(name).delete();
            client.services().inNamespace(namespace).withName(name).delete();
        }
    }

    public void runAbTest(AbTestRequest request) {
        String serviceName = request.getServiceName();
        if (Boolean.TRUE.equals(runningTests.get(serviceName))) {
            log.warn("A/B test already running for {}", serviceName);
            return;
        }
        runningTests.put(serviceName, true);

        executor.submit(() -> {
            List<Process> portForwards = new ArrayList<>();
            try {
                var svcConfig = kubernetesProperties.getServices().stream()
                        .filter(s -> s.getName().equals(serviceName))
                        .findFirst()
                        .orElseThrow();

                int jvmLocalPort = 19100 + new Random().nextInt(100);
                int nativeLocalPort = jvmLocalPort + 1;

                portForwards.add(startPortForward(serviceName + "-ab-jvm", jvmLocalPort, svcConfig.getPort()));
                portForwards.add(startPortForward(serviceName + "-ab-native", nativeLocalPort, svcConfig.getPort()));

                Thread.sleep(3000); // laisser le temps aux port-forwards de s'etablir

                String path = request.getTestPath() != null ? request.getTestPath() : "/actuator/health";
                int total = request.getTotalRequests();
                double jvmSplit = request.getJvmSplitPercent() / 100.0;
                int jvmCount = (int) Math.round(total * jvmSplit);

                List<String> plan = new ArrayList<>();
                for (int i = 0; i < jvmCount; i++) plan.add("JVM");
                for (int i = 0; i < total - jvmCount; i++) plan.add("NATIVE");
                Collections.shuffle(plan);

                for (int i = 0; i < plan.size(); i++) {
                    String variant = plan.get(i);
                    int port = variant.equals("JVM") ? jvmLocalPort : nativeLocalPort;
                    AbTestResult result = callAndMeasure(variant, port, path, i + 1);
                    emit(serviceName, result);
                    Thread.sleep(50);
                }

            } catch (Exception e) {
                log.error("A/B test failed for {}", serviceName, e);
            } finally {
                portForwards.forEach(Process::destroy);
                runningTests.put(serviceName, false);
                Sinks.Many<AbTestResult> sink = testSinks.get(serviceName);
                if (sink != null) sink.tryEmitComplete();
            }
        });
    }

    private Process startPortForward(String serviceName, int localPort, int remotePort) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                String.format("kubectl port-forward -n %s svc/%s %d:%d",
                        kubernetesProperties.getNamespace(), serviceName, localPort, remotePort));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")));
        return pb.start();
    }

    private AbTestResult callAndMeasure(String variant, int port, String path, int requestNumber) {
        long start = System.nanoTime();
        int statusCode;
        boolean success;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            statusCode = resp.statusCode();
            success = statusCode >= 200 && statusCode < 400;
        } catch (Exception e) {
            statusCode = 0;
            success = false;
        }
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        return AbTestResult.builder()
                .variant(variant)
                .requestNumber(requestNumber)
                .latencyMs(Math.round(latencyMs * 100.0) / 100.0)
                .statusCode(statusCode)
                .success(success)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public List<AbTestStats> computeStats(List<AbTestResult> results) {
        List<AbTestStats> stats = new ArrayList<>();
        for (String variant : List.of("JVM", "NATIVE")) {
            List<AbTestResult> filtered = results.stream()
                    .filter(r -> r.getVariant().equals(variant))
                    .toList();
            if (filtered.isEmpty()) continue;

            List<Double> latencies = filtered.stream()
                    .map(AbTestResult::getLatencyMs)
                    .sorted()
                    .toList();

            double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double min = latencies.get(0);
            double max = latencies.get(latencies.size() - 1);
            int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
            double p95 = latencies.get(Math.max(0, Math.min(p95Index, latencies.size() - 1)));

            long successCount = filtered.stream().filter(AbTestResult::isSuccess).count();

            stats.add(AbTestStats.builder()
                    .variant(variant)
                    .totalRequests(filtered.size())
                    .successCount((int) successCount)
                    .errorCount(filtered.size() - (int) successCount)
                    .avgLatencyMs(Math.round(avg * 100.0) / 100.0)
                    .minLatencyMs(min)
                    .maxLatencyMs(max)
                    .p95LatencyMs(p95)
                    .build());
        }
        return stats;
    }

    private void emit(String serviceName, AbTestResult result) {
        Sinks.Many<AbTestResult> sink = testSinks.get(serviceName);
        if (sink != null) sink.tryEmitNext(result);
        testResults.computeIfAbsent(serviceName, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(result);
    }

    public List<AbTestResult> getResults(String serviceName) {
        return testResults.getOrDefault(serviceName, List.of());
    }

    public void clearResults(String serviceName) {
        testResults.remove(serviceName);
        testSinks.remove(serviceName);
    }
}
