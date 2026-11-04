package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BenchmarkHistory {
    private String service;
    private String mode;
    private String metric;
    private List<DataPoint> dataPoints;
    private Double trend; // pente de regression lineaire (+ = degradation, - = amelioration)
    private String trendLabel; // IMPROVING, STABLE, DEGRADING

    @Data
    @Builder
    public static class DataPoint {
        private String runDate;
        private Double value;
        private boolean isRegression;
    }
}
