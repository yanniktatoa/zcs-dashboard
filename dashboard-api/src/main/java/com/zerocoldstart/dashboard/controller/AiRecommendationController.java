package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.service.OllamaService;
import com.zerocoldstart.dashboard.service.RecommendationService;
import com.zerocoldstart.dashboard.model.BuildRecommendation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final OllamaService ollamaService;
    private final RecommendationService recommendationService;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestBody Map<String, String> body) {
        String serviceName = body.getOrDefault("serviceName", "unknown");
        String logContent = body.getOrDefault("logContent", "");

        if (logContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "logContent est requis"
            ));
        }

        // 1. Analyse par regles - precise et deterministe
        List<BuildRecommendation> rules = recommendationService.analyzeFromLog(serviceName, logContent);

        // 2. Si des erreurs trouvees par les regles, enrichir avec Ollama
        String aiAnalysis = "";
        if (!rules.isEmpty()) {
            StringBuilder summary = new StringBuilder();
            for (BuildRecommendation r : rules) {
                summary.append("- ").append(r.getSeverity()).append(" [").append(r.getCategory()).append("] : ")
                       .append(r.getDescription()).append("\n")
                       .append("  Actuel: ").append(r.getCurrentValue()).append("\n")
                       .append("  Recommande: ").append(r.getRecommendedValue()).append("\n\n");
            }
            aiAnalysis = ollamaService.analyze(serviceName, summary.toString());
        } else {
            aiAnalysis = "Aucune erreur critique GraalVM détectée dans ces logs. " +
                         "Le service semble compiler normalement.";
        }

        return ResponseEntity.ok(Map.of(
            "service", serviceName,
            "rules", rules,
            "analysis", aiAnalysis
        ));
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeStream(
            @RequestBody Map<String, String> body) {
        String serviceName = body.getOrDefault("serviceName", "unknown");
        String logContent = body.getOrDefault("logContent", "");

        if (logContent.isBlank()) {
            return Flux.just("Veuillez fournir des logs a analyser.");
        }

        return ollamaService.analyzeStream(serviceName, logContent);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String test = ollamaService.analyze("test", "test");
            return ResponseEntity.ok(Map.of(
                "available", true,
                "model", "llama3.2:3b"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "available", false,
                "error", e.getMessage()
            ));
        }
    }
}
