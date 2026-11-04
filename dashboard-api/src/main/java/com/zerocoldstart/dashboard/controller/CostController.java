package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.CostEstimate;
import com.zerocoldstart.dashboard.service.CostSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostSimulationService costSimulationService;

    @GetMapping("/simulate")
    public ResponseEntity<List<CostEstimate>> simulate(
            @RequestParam(defaultValue = "1000000") long invocationsPerMonth,
            @RequestParam(defaultValue = "5") double coldStartRatioPercent) {
        return ResponseEntity.ok(
                costSimulationService.simulate(invocationsPerMonth, coldStartRatioPercent));
    }
}
