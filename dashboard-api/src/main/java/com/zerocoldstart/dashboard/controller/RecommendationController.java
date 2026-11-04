package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.BuildRecommendation;
import com.zerocoldstart.dashboard.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{serviceName}")
    public ResponseEntity<List<BuildRecommendation>> getRecommendations(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(recommendationService.analyzeLastBuild(serviceName));
    }

    @PostMapping("/analyze")
    public ResponseEntity<List<BuildRecommendation>> analyzeLog(
            @RequestBody Map<String, String> body) {
        String serviceName = body.get("serviceName");
        String logContent = body.get("logContent");
        return ResponseEntity.ok(recommendationService.analyzeFromLog(serviceName, logContent));
    }
}
