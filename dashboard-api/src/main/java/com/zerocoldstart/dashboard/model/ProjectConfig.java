package com.zerocoldstart.dashboard.model;

import lombok.Data;

@Data
public class ProjectConfig {
    private String basePath;
    private String namespace;
    private String clusterName;
}
