package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.BuildRequest;
import com.zerocoldstart.dashboard.model.SseEvent;
import com.zerocoldstart.dashboard.service.BuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/build")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;

    @PostMapping("/jvm")
    public ResponseEntity<Map<String, String>> startJvmBuild(
            @RequestBody BuildRequest request) {
        if (buildService.isBuildRunning(request.getServiceName())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Build already running for " + request.getServiceName()
            ));
        }
        buildService.startJvmBuild(request.getServiceName());
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "mode", "jvm",
                "service", request.getServiceName()
        ));
    }

    @PostMapping("/native")
    public ResponseEntity<Map<String, String>> startNativeBuild(
            @RequestBody BuildRequest request) {
        if (buildService.isBuildRunning(request.getServiceName())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Build already running for " + request.getServiceName()
            ));
        }
        buildService.startNativeBuild(request.getServiceName());
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "service", request.getServiceName()
        ));
    }

    @GetMapping(value = "/stream/{serviceName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SseEvent> streamBuildLogs(@PathVariable String serviceName) {
        return buildService.streamBuildLogs(serviceName);
    }

    @GetMapping("/status/{serviceName}")
    public ResponseEntity<Map<String, Object>> getBuildStatus(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(Map.of(
                "service", serviceName,
                "running", buildService.isBuildRunning(serviceName)
        ));
    }

    @DeleteMapping("/cancel/{serviceName}")
    public ResponseEntity<Map<String, String>> cancelBuild(
            @PathVariable String serviceName) {
        buildService.cancelBuild(serviceName);
        return ResponseEntity.ok(Map.of(
                "status", "cancelled",
                "service", serviceName
        ));
    }
}
