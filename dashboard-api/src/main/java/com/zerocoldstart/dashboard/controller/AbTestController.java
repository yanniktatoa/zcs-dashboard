package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.AbTestRequest;
import com.zerocoldstart.dashboard.model.AbTestResult;
import com.zerocoldstart.dashboard.service.AbTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/abtest")
@RequiredArgsConstructor
public class AbTestController {

    private final AbTestService abTestService;

    @PostMapping("/setup/{serviceName}")
    public ResponseEntity<Map<String, String>> setup(@PathVariable String serviceName) {
        abTestService.setupAbDeployments(serviceName);
        return ResponseEntity.ok(Map.of("status", "deployed", "service", serviceName));
    }

    @DeleteMapping("/teardown/{serviceName}")
    public ResponseEntity<Map<String, String>> teardown(@PathVariable String serviceName) {
        abTestService.teardownAbDeployments(serviceName);
        return ResponseEntity.ok(Map.of("status", "removed", "service", serviceName));
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runTest(@RequestBody AbTestRequest request) {
        if (abTestService.isRunning(request.getServiceName())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Test deja en cours pour " + request.getServiceName()
            ));
        }
        abTestService.runAbTest(request);
        return ResponseEntity.ok(Map.of("status", "started", "service", request.getServiceName()));
    }

    @GetMapping(value = "/stream/{serviceName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AbTestResult> stream(@PathVariable String serviceName) {
        return abTestService.streamResults(serviceName);
    }

    @GetMapping("/status/{serviceName}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String serviceName) {
        return ResponseEntity.ok(Map.of(
                "service", serviceName,
                "running", abTestService.isRunning(serviceName)
        ));
    }

    @GetMapping("/results/{serviceName}")
    public ResponseEntity<List<AbTestResult>> getResults(@PathVariable String serviceName) {
        return ResponseEntity.ok(abTestService.getResults(serviceName));
    }

    @GetMapping("/stats/{serviceName}")
    public ResponseEntity<?> getStats(@PathVariable String serviceName) {
        List<AbTestResult> results = abTestService.getResults(serviceName);
        return ResponseEntity.ok(abTestService.computeStats(results));
    }

    @DeleteMapping("/results/{serviceName}")
    public ResponseEntity<Map<String, String>> clearResults(@PathVariable String serviceName) {
        abTestService.clearResults(serviceName);
        return ResponseEntity.ok(Map.of("status", "cleared", "service", serviceName));
    }
}
