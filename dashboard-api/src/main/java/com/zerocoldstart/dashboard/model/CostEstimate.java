package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CostEstimate {
    private String provider;
    private String service;
    private String mode;
    private long invocationsPerMonth;
    private double avgDurationMs;
    private double memoryMb;
    private double computeCostUsd;
    private double requestCostUsd;
    private double coldStartOverheadCostUsd;
    private double totalCostUsd;
}
