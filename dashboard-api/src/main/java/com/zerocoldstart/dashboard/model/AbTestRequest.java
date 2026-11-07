package com.zerocoldstart.dashboard.model;

import lombok.Data;

@Data
public class AbTestRequest {
    private String serviceName;
    private int totalRequests;
    private double jvmSplitPercent;
    private String testPath; // ex: /actuator/health
    private int warmupRequests = 200; // par variante, non comptabilisees
}
