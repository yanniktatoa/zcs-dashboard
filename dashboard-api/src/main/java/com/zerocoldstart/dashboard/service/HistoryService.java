package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import com.zerocoldstart.dashboard.model.BenchmarkHistory;
import com.zerocoldstart.dashboard.model.BenchmarkHistory.DataPoint;
import com.zerocoldstart.dashboard.model.RegressionAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ProjectProperties projectProperties;
    private final RegressionService regressionService;

    private static final List<String> SERVICES = List.of(
            "catalog-service", "user-service", "loan-service", "library-gateway"
    );
    private static final List<String> METRICS = List.of("coldStart", "memory", "imageSize");

    public List<BenchmarkHistory> getFullHistory(String mode) {
        List<BenchmarkHistory> result = new ArrayList<>();
        for (String service : SERVICES) {
            for (String metric : METRICS) {
                BenchmarkHistory history = buildHistory(mode, service, metric);
                if (history != null) result.add(history);
            }
        }
        return result;
    }

    public BenchmarkHistory getServiceHistory(String mode, String service, String metric) {
        return buildHistory(mode, service, metric);
    }

    public Map<String, Object> getTrendSummary(String mode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String service : SERVICES) {
            Map<String, Object> svcTrends = new LinkedHashMap<>();
            for (String metric : METRICS) {
                BenchmarkHistory h = buildHistory(mode, service, metric);
                if (h != null) {
                    svcTrends.put(metric, Map.of(
                            "trend", h.getTrend() != null ? h.getTrend() : 0,
                            "label", h.getTrendLabel() != null ? h.getTrendLabel() : "STABLE",
                            "dataPoints", h.getDataPoints().size()
                    ));
                }
            }
            summary.put(service, svcTrends);
        }
        return summary;
    }

    private BenchmarkHistory buildHistory(String mode, String service, String metric) {
        List<String> runs = getSortedRunDirs(mode);
        Collections.reverse(runs);

        List<DataPoint> dataPoints = new ArrayList<>();
        List<RegressionAlert> alerts = regressionService.getHistory(mode, service, metric);
        Set<String> regressionDates = alerts.stream()
                .filter(RegressionAlert::isRegression)
                .map(RegressionAlert::getCurrentRunDate)
                .collect(Collectors.toSet());

        for (String run : runs) {
            Double value = readMetricValue(run, service, metric, mode);
            String date = Paths.get(run).getFileName().toString();
            if (value != null) {
                dataPoints.add(DataPoint.builder()
                        .runDate(date)
                        .value(Math.round(value * 100.0) / 100.0)
                        .isRegression(regressionDates.contains(date))
                        .build());
            }
        }

        if (dataPoints.isEmpty()) return null;

        double trend = computeTrend(dataPoints);
        String trendLabel = trend > 0.05 ? "DEGRADING" : trend < -0.05 ? "IMPROVING" : "STABLE";

        return BenchmarkHistory.builder()
                .service(service)
                .mode(mode)
                .metric(metric)
                .dataPoints(dataPoints)
                .trend(Math.round(trend * 1000.0) / 1000.0)
                .trendLabel(trendLabel)
                .build();
    }

    private double computeTrend(List<DataPoint> points) {
        if (points.size() < 2) return 0;
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += points.get(i).getValue();
            sumXY += i * points.get(i).getValue();
            sumX2 += i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (denom == 0) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private List<String> getSortedRunDirs(String mode) {
        try {
            Path resultsPath = Paths.get(projectProperties.getResultsPath());
            if (!Files.exists(resultsPath)) return Collections.emptyList();
            return Files.list(resultsPath)
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("coldstart").resolve("coldstart-" + mode + ".csv"))
                            || Files.exists(p.resolve("startup").resolve("startup-" + mode + ".csv")))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing runs", e);
            return Collections.emptyList();
        }
    }

    private Double readMetricValue(String runDir, String service, String metric, String mode) {
        try {
            return switch (metric) {
                case "coldStart" -> {
                    Path f = Paths.get(runDir, "coldstart", "coldstart-" + mode + ".csv");
                    if (!Files.exists(f)) yield null;
                    try (BufferedReader br = new BufferedReader(new FileReader(f.toFile()))) {
                        OptionalDouble avg = br.lines().skip(1)
                                .filter(l -> l.startsWith(service + ","))
                                .mapToDouble(l -> Double.parseDouble(l.split(",")[2]))
                                .average();
                        yield avg.isPresent() ? avg.getAsDouble() : null;
                    }
                }
                case "memory" -> {
                    Path f = Paths.get(runDir, "memory", "memory-" + mode + ".csv");
                    if (!Files.exists(f)) yield null;
                    try (BufferedReader br = new BufferedReader(new FileReader(f.toFile()))) {
                        yield br.lines().skip(1)
                                .filter(l -> l.startsWith(service + ",idle,"))
                                .map(l -> Double.parseDouble(l.split(",")[3]))
                                .findFirst().orElse(null);
                    }
                }
                case "imageSize" -> {
                    Path f = Paths.get(runDir, "images", "image-sizes-" + mode + ".csv");
                    if (!Files.exists(f)) yield null;
                    try (BufferedReader br = new BufferedReader(new FileReader(f.toFile()))) {
                        yield br.lines().skip(1)
                                .filter(l -> l.startsWith(service + ","))
                                .map(l -> Double.parseDouble(l.split(",")[2]))
                                .findFirst().orElse(null);
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
