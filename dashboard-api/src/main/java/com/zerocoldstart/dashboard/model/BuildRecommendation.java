package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BuildRecommendation {
    private String serviceName;
    private String category; // MEMORY, DEADLOCK, REFLECTION, PROXY, LOGGING
    private String severity; // INFO, WARNING, CRITICAL
    private String description;
    private String currentValue;
    private String recommendedValue;
    private String buildArgToAdd;
    private String explanation;
}
