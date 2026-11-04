package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.BenchmarkHistory;
import com.zerocoldstart.dashboard.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/{mode}")
    public ResponseEntity<List<BenchmarkHistory>> getFullHistory(@PathVariable String mode) {
        return ResponseEntity.ok(historyService.getFullHistory(mode));
    }

    @GetMapping("/{mode}/{service}/{metric}")
    public ResponseEntity<BenchmarkHistory> getServiceHistory(
            @PathVariable String mode,
            @PathVariable String service,
            @PathVariable String metric) {
        return ResponseEntity.ok(historyService.getServiceHistory(mode, service, metric));
    }

    @GetMapping("/trends/{mode}")
    public ResponseEntity<Map<String, Object>> getTrends(@PathVariable String mode) {
        return ResponseEntity.ok(historyService.getTrendSummary(mode));
    }
}
