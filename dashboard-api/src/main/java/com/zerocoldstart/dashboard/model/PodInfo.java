package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PodInfo {
    private String name;
    private String service;
    private String status;
    private String image;
    private String mode; // JVM ou NATIVE
    private int restarts;
    private String age;
    private String cpu;
    private String memory;
    private boolean ready;
}
