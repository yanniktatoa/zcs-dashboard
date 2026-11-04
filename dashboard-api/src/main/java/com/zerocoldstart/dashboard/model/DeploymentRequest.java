package com.zerocoldstart.dashboard.model;

import lombok.Data;

@Data
public class DeploymentRequest {
    private String serviceName;
    private String mode; // JVM ou NATIVE
}
