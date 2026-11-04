package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PodLogsService {

    private final KubernetesClient client;
    private final KubernetesProperties kubernetesProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<String, reactor.core.publisher.Sinks.Many<String>> logSinks
            = new ConcurrentHashMap<>();
    private final Map<String, Boolean> activeStreams = new ConcurrentHashMap<>();

    public Flux<String> streamPodLogs(String podName) {
        stopStream(podName);

        reactor.core.publisher.Sinks.Many<String> sink =
                reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        logSinks.put(podName, sink);
        activeStreams.put(podName, true);

        executor.submit(() -> {
            try {
                Process process = new ProcessBuilder("bash", "-c",
                        String.format("kubectl logs -n %s %s -f --tail=100",
                                kubernetesProperties.getNamespace(), podName))
                        .redirectErrorStream(true)
                        .start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null
                            && Boolean.TRUE.equals(activeStreams.get(podName))) {
                        sink.tryEmitNext(line);
                    }
                }
                process.destroy();
            } catch (Exception e) {
                log.error("Error streaming logs for pod {}", podName, e);
            } finally {
                activeStreams.put(podName, false);
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux()
                .timeout(Duration.ofMinutes(10))
                .onErrorResume(e -> Flux.empty());
    }

    public String getPodStaticLogs(String podName, int tailLines) {
        try {
            return client.pods()
                    .inNamespace(kubernetesProperties.getNamespace())
                    .withName(podName)
                    .tailingLines(tailLines)
                    .getLog();
        } catch (Exception e) {
            log.error("Error getting logs for pod {}", podName, e);
            return "Error fetching logs: " + e.getMessage();
        }
    }

    public void stopStream(String podName) {
        activeStreams.put(podName, false);
        reactor.core.publisher.Sinks.Many<String> sink = logSinks.get(podName);
        if (sink != null) {
            sink.tryEmitComplete();
            logSinks.remove(podName);
        }
    }
}
