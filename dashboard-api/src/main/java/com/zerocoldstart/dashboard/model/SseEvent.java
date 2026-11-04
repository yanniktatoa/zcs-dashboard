package com.zerocoldstart.dashboard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SseEvent {
    private String type; // LOG, PROGRESS, SUCCESS, ERROR, METRIC
    private String message;
    private Integer progress; // 0-100
    private Object data;
    private long timestamp;
}
