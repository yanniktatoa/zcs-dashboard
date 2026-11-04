package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.DeploymentRequest;
import com.zerocoldstart.dashboard.model.PodInfo;
import com.zerocoldstart.dashboard.service.KubernetesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
public class ClusterController {

    private final KubernetesService kubernetesService;

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getClusterInfo() {
        return ResponseEntity.ok(kubernetesService.getClusterInfo());
    }

    @GetMapping("/pods")
    public ResponseEntity<List<PodInfo>> getAllPods() {
        return ResponseEntity.ok(kubernetesService.getAllPods());
    }

    @PostMapping("/deploy")
    public ResponseEntity<Map<String, String>> switchMode(
            @RequestBody DeploymentRequest request) {
        log.info("Switching {} to mode {}", request.getServiceName(), request.getMode());
        kubernetesService.switchMode(request.getServiceName(), request.getMode());
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", request.getServiceName(),
                "mode", request.getMode()
        ));
    }

    @PostMapping("/scale/{serviceName}/{replicas}")
    public ResponseEntity<Map<String, String>> scale(
            @PathVariable String serviceName,
            @PathVariable int replicas) {
        kubernetesService.scaleDeployment(serviceName, replicas);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", serviceName,
                "replicas", String.valueOf(replicas)
        ));
    }

    @PostMapping("/restart/{serviceName}")
    public ResponseEntity<Map<String, String>> restart(
            @PathVariable String serviceName) {
        kubernetesService.restartDeployment(serviceName);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", serviceName
        ));
    }

    @GetMapping("/mode/{serviceName}")
    public ResponseEntity<Map<String, String>> getMode(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(Map.of(
                "service", serviceName,
                "mode", kubernetesService.getDeploymentMode(serviceName)
        ));
    }

    @GetMapping("/logs/{serviceName}")
    public ResponseEntity<Map<String, String>> getLogs(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(Map.of(
                "service", serviceName,
                "logs", kubernetesService.getPodLogs(serviceName, 100)
        ));
    }

    @GetMapping("/services/discover")
    public ResponseEntity<List<Map<String, Object>>> discoverServices() {
        return ResponseEntity.ok(kubernetesService.discoverServices());
    }
}
