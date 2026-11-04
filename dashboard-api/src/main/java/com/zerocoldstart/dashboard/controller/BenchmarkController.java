package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.SseEvent;
import com.zerocoldstart.dashboard.service.BenchmarkService;
import com.zerocoldstart.dashboard.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;
    private final MetricsService metricsService;

    @PostMapping("/start/{mode}")
    public ResponseEntity<Map<String, String>> startBenchmark(
            @PathVariable String mode) {
        if (!mode.equals("jvm") && !mode.equals("native")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Mode must be 'jvm' or 'native'"
            ));
        }
        if (benchmarkService.isBenchmarkRunning(mode)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Benchmark already running for mode " + mode
            ));
        }
        benchmarkService.startBenchmark(mode);
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "mode", mode
        ));
    }

    @GetMapping(value = "/stream/{mode}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SseEvent> streamBenchmark(@PathVariable String mode) {
        return benchmarkService.streamBenchmarkLogs(mode);
    }

    @GetMapping("/status/{mode}")
    public ResponseEntity<Map<String, Object>> getBenchmarkStatus(
            @PathVariable String mode) {
        return ResponseEntity.ok(Map.of(
                "mode", mode,
                "running", benchmarkService.isBenchmarkRunning(mode)
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, ?>> getAllMetrics() {
        return ResponseEntity.ok(metricsService.getAllMetrics());
    }

    @GetMapping("/metrics/{mode}")
    public ResponseEntity<?> getMetricsByMode(@PathVariable String mode) {
        return ResponseEntity.ok(metricsService.getMetricsForMode(mode));
    }

    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> getComparison() {
        return ResponseEntity.ok(metricsService.getComparisonSummary());
    }
}
