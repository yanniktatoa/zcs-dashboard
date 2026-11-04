package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.model.CostEstimate;
import com.zerocoldstart.dashboard.model.MetricResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CostSimulationService {

    private final MetricsService metricsService;

    private static final long AWS_FREE_REQUESTS = 1_000_000;
    private static final long AWS_FREE_GB_SECONDS = 400_000;

    private static final long GCP_FREE_REQUESTS = 2_000_000;
    private static final double GCP_FREE_VCPU_SECONDS = 180_000;
    private static final double GCP_FREE_GB_SECONDS = 360_000;

    private static final long AZURE_FREE_REQUESTS = 2_000_000;
    private static final double AZURE_FREE_VCPU_SECONDS = 180_000;
    private static final double AZURE_FREE_GB_SECONDS = 360_000;

    private static final double AWS_PRICE_PER_REQUEST = 0.20 / 1_000_000;
    private static final double AWS_PRICE_PER_GB_SECOND = 0.0000166667;

    private static final double GCP_PRICE_PER_REQUEST = 0.40 / 1_000_000;
    private static final double GCP_PRICE_PER_VCPU_SECOND = 0.000024;
    private static final double GCP_PRICE_PER_GB_SECOND = 0.0000025;

    private static final double AZURE_PRICE_PER_REQUEST = 0.40 / 1_000_000;
    private static final double AZURE_PRICE_PER_VCPU_SECOND = 0.000024;
    private static final double AZURE_PRICE_PER_GB_SECOND = 0.0000025;

    public List<CostEstimate> simulate(long invocationsPerMonth, double coldStartRatioPercent) {
        List<CostEstimate> estimates = new ArrayList<>();

        Map<String, List<MetricResult>> allMetrics = metricsService.getAllMetrics();
        List<MetricResult> jvmMetrics = allMetrics.getOrDefault("jvm", List.of());
        List<MetricResult> nativeMetrics = allMetrics.getOrDefault("native", List.of());

        for (MetricResult jvm : jvmMetrics) {
            estimates.addAll(computeForService(jvm, invocationsPerMonth, coldStartRatioPercent));
        }
        for (MetricResult native_ : nativeMetrics) {
            estimates.addAll(computeForService(native_, invocationsPerMonth, coldStartRatioPercent));
        }

        return estimates;
    }

    private List<CostEstimate> computeForService(MetricResult metric, long invocationsPerMonth,
                                                  double coldStartRatioPercent) {
        List<CostEstimate> estimates = new ArrayList<>();

        double memoryMb = metric.getMemoryIdleMi() != null ? metric.getMemoryIdleMi() : 512;
        double coldStartMs = metric.getColdStartS() != null ? metric.getColdStartS() * 1000 : 0;
        double warmDurationMs = metric.getAvgLatencyMs() != null ? metric.getAvgLatencyMs() : 50;

        double coldRatio = coldStartRatioPercent / 100.0;
        double avgDurationMs = (coldRatio * (coldStartMs + warmDurationMs))
                + ((1 - coldRatio) * warmDurationMs);

        long coldInvocations = Math.round(invocationsPerMonth * coldRatio);

        estimates.add(computeAws(metric, invocationsPerMonth, avgDurationMs, memoryMb,
                coldInvocations, coldStartMs));
        estimates.add(computeGcp(metric, invocationsPerMonth, avgDurationMs, memoryMb,
                coldInvocations, coldStartMs));
        estimates.add(computeAzure(metric, invocationsPerMonth, avgDurationMs, memoryMb,
                coldInvocations, coldStartMs));

        return estimates;
    }

    private CostEstimate computeAws(MetricResult metric, long invocations, double avgDurationMs,
                                     double memoryMb, long coldInvocations, double coldStartMs) {
        double gbSeconds = invocations * (avgDurationMs / 1000.0) * (memoryMb / 1024.0);
        double billableGbSeconds = Math.max(0, gbSeconds - AWS_FREE_GB_SECONDS);
        double billableRequests = Math.max(0, invocations - AWS_FREE_REQUESTS);

        double computeCost = billableGbSeconds * AWS_PRICE_PER_GB_SECOND;
        double requestCost = billableRequests * AWS_PRICE_PER_REQUEST;

        double coldStartGbSeconds = coldInvocations * (coldStartMs / 1000.0) * (memoryMb / 1024.0);
        double coldOverhead = coldStartGbSeconds * AWS_PRICE_PER_GB_SECOND;

        return CostEstimate.builder()
                .provider("AWS Lambda")
                .service(metric.getService())
                .mode(metric.getMode())
                .invocationsPerMonth(invocations)
                .avgDurationMs(round(avgDurationMs))
                .memoryMb(memoryMb)
                .computeCostUsd(round(computeCost))
                .requestCostUsd(round(requestCost))
                .coldStartOverheadCostUsd(round(coldOverhead))
                .totalCostUsd(round(computeCost + requestCost))
                .build();
    }

    private CostEstimate computeGcp(MetricResult metric, long invocations, double avgDurationMs,
                                    double memoryMb, long coldInvocations, double coldStartMs) {
        double vcpuSeconds = invocations * (avgDurationMs / 1000.0);
        double gbSeconds = invocations * (avgDurationMs / 1000.0) * (memoryMb / 1024.0);

        double billableVcpuSeconds = Math.max(0, vcpuSeconds - GCP_FREE_VCPU_SECONDS);
        double billableGbSeconds = Math.max(0, gbSeconds - GCP_FREE_GB_SECONDS);
        double billableRequests = Math.max(0, invocations - GCP_FREE_REQUESTS);

        double computeCost = (billableVcpuSeconds * GCP_PRICE_PER_VCPU_SECOND)
                + (billableGbSeconds * GCP_PRICE_PER_GB_SECOND);
        double requestCost = billableRequests * GCP_PRICE_PER_REQUEST;

        double coldVcpuSeconds = coldInvocations * (coldStartMs / 1000.0);
        double coldOverhead = coldVcpuSeconds * GCP_PRICE_PER_VCPU_SECOND;

        return CostEstimate.builder()
                .provider("Google Cloud Run")
                .service(metric.getService())
                .mode(metric.getMode())
                .invocationsPerMonth(invocations)
                .avgDurationMs(round(avgDurationMs))
                .memoryMb(memoryMb)
                .computeCostUsd(round(computeCost))
                .requestCostUsd(round(requestCost))
                .coldStartOverheadCostUsd(round(coldOverhead))
                .totalCostUsd(round(computeCost + requestCost))
                .build();
    }

    private CostEstimate computeAzure(MetricResult metric, long invocations, double avgDurationMs,
                                       double memoryMb, long coldInvocations, double coldStartMs) {
        double vcpuSeconds = invocations * (avgDurationMs / 1000.0);
        double gbSeconds = invocations * (avgDurationMs / 1000.0) * (memoryMb / 1024.0);

        double billableVcpuSeconds = Math.max(0, vcpuSeconds - AZURE_FREE_VCPU_SECONDS);
        double billableGbSeconds = Math.max(0, gbSeconds - AZURE_FREE_GB_SECONDS);
        double billableRequests = Math.max(0, invocations - AZURE_FREE_REQUESTS);

        double computeCost = (billableVcpuSeconds * AZURE_PRICE_PER_VCPU_SECOND)
                + (billableGbSeconds * AZURE_PRICE_PER_GB_SECOND);
        double requestCost = billableRequests * AZURE_PRICE_PER_REQUEST;

        double coldVcpuSeconds = coldInvocations * (coldStartMs / 1000.0);
        double coldOverhead = coldVcpuSeconds * AZURE_PRICE_PER_VCPU_SECOND;

        return CostEstimate.builder()
                .provider("Azure Container Apps")
                .service(metric.getService())
                .mode(metric.getMode())
                .invocationsPerMonth(invocations)
                .avgDurationMs(round(avgDurationMs))
                .memoryMb(memoryMb)
                .computeCostUsd(round(computeCost))
                .requestCostUsd(round(requestCost))
                .coldStartOverheadCostUsd(round(coldOverhead))
                .totalCostUsd(round(computeCost + requestCost))
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
