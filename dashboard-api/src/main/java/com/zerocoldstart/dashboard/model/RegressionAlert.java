package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegressionAlert {
    private String service;
    private String metric; // coldStart, memory, imageSize
    private Double previousValue;
    private Double currentValue;
    private Double changePercent;
    private boolean isRegression; // true si degradation, false si amelioration
    private String previousRunDate;
    private String currentRunDate;
}
