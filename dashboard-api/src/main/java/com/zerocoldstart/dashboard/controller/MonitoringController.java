package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.PodInfo;
import com.zerocoldstart.dashboard.service.KubernetesService;
import com.zerocoldstart.dashboard.service.PodLogsService;
import com.zerocoldstart.dashboard.service.PrometheusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final KubernetesService kubernetesService;
    private final PodLogsService podLogsService;
    private final PrometheusService prometheusService;

    /**
     * SSE stream qui emet l'etat des pods toutes les 10 secondes.
     * Le frontend s'y abonne une fois et recoit les mises a jour automatiquement.
     */
    @GetMapping(value = "/pods/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<List<PodInfo>> livePods() {
        return Flux.interval(Duration.ofSeconds(10))
                .map(tick -> kubernetesService.getAllPods())
                .startWith(kubernetesService.getAllPods());
    }

    /**
     * SSE stream des logs d'un pod specifique en temps reel.
     */
    @GetMapping(value = "/logs/{podName}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamLogs(@PathVariable String podName) {
        return podLogsService.streamPodLogs(podName);
    }

    /**
     * Logs statiques d'un pod (snapshot).
     */
    @GetMapping("/logs/{podName}")
    public ResponseEntity<Map<String, String>> getLogs(
            @PathVariable String podName,
            @RequestParam(defaultValue = "100") int lines) {
        return ResponseEntity.ok(Map.of(
                "pod", podName,
                "logs", podLogsService.getPodStaticLogs(podName, lines)
        ));
    }

    @DeleteMapping("/logs/{podName}/stream")
    public ResponseEntity<Map<String, String>> stopLogStream(@PathVariable String podName) {
        podLogsService.stopStream(podName);
        return ResponseEntity.ok(Map.of("status", "stopped", "pod", podName));
    }

    /**
     * Metriques Prometheus en temps reel pour tous les services.
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<Map<String, Object>>> getMetrics() {
        return ResponseEntity.ok(prometheusService.getAllServicesMetrics());
    }

    @GetMapping("/metrics/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceMetrics(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(prometheusService.getMetrics(serviceName));
    }

    /**
     * SSE stream des metriques Prometheus toutes les 15 secondes.
     */
    @GetMapping(value = "/metrics/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<List<Map<String, Object>>> liveMetrics() {
        return Flux.interval(Duration.ofSeconds(15))
                .map(tick -> prometheusService.getAllServicesMetrics())
                .startWith(prometheusService.getAllServicesMetrics());
    }

    /**
     * Configurer l'URL de Prometheus (utile si port-forward ou ingress different).
     */
    @PutMapping("/prometheus/url")
    public ResponseEntity<Map<String, String>> setPrometheusUrl(
            @RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url != null && !url.isBlank()) {
            prometheusService.setPrometheusUrl(url);
            return ResponseEntity.ok(Map.of("status", "updated", "url", url));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "url manquante"));
    }
}
