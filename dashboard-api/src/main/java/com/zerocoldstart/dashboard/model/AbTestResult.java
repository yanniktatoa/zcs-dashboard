package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AbTestResult {
    private String variant; // JVM ou NATIVE
    private int requestNumber;
    private double latencyMs;
    private int statusCode;
    private boolean success;
    private long timestamp;
}
