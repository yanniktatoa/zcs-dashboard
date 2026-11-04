package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricResult {
    private String service;
    private String mode;
    private Double startupMs;
    private Double coldStartS;
    private Double memoryIdleMi;
    private Double memoryWarmMi;
    private Double imageSizeMb;
    private Double throughputRps;
    private Double avgLatencyMs;
    private String measuredAt;
}
