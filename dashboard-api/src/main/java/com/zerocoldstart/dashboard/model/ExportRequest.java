package com.zerocoldstart.dashboard.model;

import lombok.Data;

@Data
public class ExportRequest {
    private String format; // PDF ou LATEX
    private boolean includeComparison;
    private boolean includeCostSimulation;
    private boolean includeRegression;
    private boolean includeAbTest;
    private long invocationsPerMonth;
    private double coldStartRatioPercent;
}
