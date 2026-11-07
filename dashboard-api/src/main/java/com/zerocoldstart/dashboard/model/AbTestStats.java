package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AbTestStats {
    private String variant;
    private int totalRequests;
    private int successCount;
    private int errorCount;
    private double avgLatencyMs;
    private double minLatencyMs;
    private double maxLatencyMs;
    private double medianLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
}
