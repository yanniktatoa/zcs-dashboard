package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ProjectProperties projectProperties;
    private final KubernetesProperties kubernetesProperties;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "basePath", projectProperties.getBasePath(),
                "resultsPath", projectProperties.getResultsPath(),
                "namespace", kubernetesProperties.getNamespace()
        ));
    }

    @PutMapping("/basePath")
    public ResponseEntity<Map<String, String>> updateBasePath(
            @RequestBody Map<String, String> body) {
        String newPath = body.get("basePath");
        if (newPath != null && !newPath.isBlank()) {
            projectProperties.setBasePath(newPath);
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "basePath", newPath
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "basePath manquant"));
    }

    @PutMapping("/resultsPath")
    public ResponseEntity<Map<String, String>> updateResultsPath(
            @RequestBody Map<String, String> body) {
        String newPath = body.get("resultsPath");
        if (newPath != null && !newPath.isBlank()) {
            projectProperties.setResultsPath(newPath);
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "resultsPath", newPath
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "resultsPath manquant"));
    }

    @PutMapping("/namespace")
    public ResponseEntity<Map<String, String>> updateNamespace(
            @RequestBody Map<String, String> body) {
        String ns = body.get("namespace");
        if (ns != null && !ns.isBlank()) {
            kubernetesProperties.setNamespace(ns);
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "namespace", ns
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "namespace manquant"));
    }
}
