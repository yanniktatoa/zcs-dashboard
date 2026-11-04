package com.zerocoldstart.dashboard.model;

import lombok.Data;

@Data
public class CostSimulationRequest {
    private long invocationsPerMonth;
    private double coldStartRatioPercent;
}
