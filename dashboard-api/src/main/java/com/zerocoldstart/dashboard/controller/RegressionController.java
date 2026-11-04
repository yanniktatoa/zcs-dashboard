package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.RegressionAlert;
import com.zerocoldstart.dashboard.service.RegressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regression")
@RequiredArgsConstructor
public class RegressionController {

    private final RegressionService regressionService;

    @GetMapping("/check/{mode}")
    public ResponseEntity<List<RegressionAlert>> checkRegression(
            @PathVariable String mode) {
        return ResponseEntity.ok(regressionService.checkRegression(mode));
    }

    @GetMapping("/history/{mode}/{service}/{metric}")
    public ResponseEntity<List<RegressionAlert>> getHistory(
            @PathVariable String mode,
            @PathVariable String service,
            @PathVariable String metric) {
        return ResponseEntity.ok(regressionService.getHistory(mode, service, metric));
    }
}
