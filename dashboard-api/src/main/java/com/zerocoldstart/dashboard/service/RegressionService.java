package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
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
public class RegressionService {

    private final ProjectProperties projectProperties;

    private static final double THRESHOLD_PERCENT = 10.0;
    private static final List<String> SERVICES = List.of(
            "catalog-service", "user-service", "loan-service", "library-gateway"
    );

    public List<RegressionAlert> checkRegression(String mode) {
        List<String> runs = getSortedRunDirs(mode);
        if (runs.size() < 2) {
            log.info("Not enough runs to compare for mode {} (found {})", mode, runs.size());
            return Collections.emptyList();
        }

        String currentRun = runs.get(0);
        String previousRun = runs.get(1);

        List<RegressionAlert> alerts = new ArrayList<>();

        for (String service : SERVICES) {
            alerts.addAll(compareMetric(service, mode, "coldStart",
                    readColdStart(previousRun, service, mode),
                    readColdStart(currentRun, service, mode),
                    previousRun, currentRun));

            alerts.addAll(compareMetric(service, mode, "memory",
                    readMemory(previousRun, service, mode),
                    readMemory(currentRun, service, mode),
                    previousRun, currentRun));

            alerts.addAll(compareMetric(service, mode, "imageSize",
                    readImageSize(previousRun, service, mode),
                    readImageSize(currentRun, service, mode),
                    previousRun, currentRun));
        }

        return alerts;
    }

    public List<RegressionAlert> getHistory(String mode, String service, String metric) {
        List<String> runs = getSortedRunDirs(mode);
        Collections.reverse(runs);

        List<RegressionAlert> history = new ArrayList<>();
        Double previousValue = null;
        String previousDate = null;

        for (String run : runs) {
            Double value = switch (metric) {
                case "coldStart" -> readColdStart(run, service, mode);
                case "memory" -> readMemory(run, service, mode);
                case "imageSize" -> readImageSize(run, service, mode);
                default -> null;
            };
            String date = extractDateFromPath(run);

            if (value != null) {
                if (previousValue != null) {
                    double change = ((value - previousValue) / previousValue) * 100;
                    history.add(RegressionAlert.builder()
                            .service(service)
                            .metric(metric)
                            .previousValue(previousValue)
                            .currentValue(value)
                            .changePercent(Math.round(change * 10.0) / 10.0)
                            .isRegression(change > THRESHOLD_PERCENT)
                            .previousRunDate(previousDate)
                            .currentRunDate(date)
                            .build());
                }
                previousValue = value;
                previousDate = date;
            }
        }

        return history;
    }

    private List<RegressionAlert> compareMetric(String service, String mode, String metricName,
                                                 Double previous, Double current,
                                                 String previousRun, String currentRun) {
        if (previous == null || current == null || previous == 0) {
            return Collections.emptyList();
        }

        double changePercent = ((current - previous) / previous) * 100;
        boolean isRegression = changePercent > THRESHOLD_PERCENT;

        if (Math.abs(changePercent) < THRESHOLD_PERCENT) {
            return Collections.emptyList();
        }

        RegressionAlert alert = RegressionAlert.builder()
                .service(service)
                .metric(metricName)
                .previousValue(previous)
                .currentValue(current)
                .changePercent(Math.round(changePercent * 10.0) / 10.0)
                .isRegression(isRegression)
                .previousRunDate(extractDateFromPath(previousRun))
                .currentRunDate(extractDateFromPath(currentRun))
                .build();

        return List.of(alert);
    }

    private List<String> getSortedRunDirs(String mode) {
        try {
            Path resultsPath = Paths.get(projectProperties.getResultsPath());
            if (!Files.exists(resultsPath)) return Collections.emptyList();

            return Files.list(resultsPath)
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        Path startupFile = p.resolve("startup").resolve("startup-" + mode + ".csv");
                        Path coldstartFile = p.resolve("coldstart").resolve("coldstart-" + mode + ".csv");
                        return Files.exists(startupFile) || Files.exists(coldstartFile);
                    })
                    .map(Path::toString)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing run directories", e);
            return Collections.emptyList();
        }
    }

    private String extractDateFromPath(String runDir) {
        return Paths.get(runDir).getFileName().toString();
    }

    private Double readColdStart(String runDir, String service, String mode) {
        try {
            Path file = Paths.get(runDir, "coldstart", "coldstart-" + mode + ".csv");
            if (!Files.exists(file)) return null;
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                OptionalDouble avg = br.lines()
                        .skip(1)
                        .filter(l -> l.startsWith(service + ","))
                        .mapToDouble(l -> Double.parseDouble(l.split(",")[2]))
                        .average();
                return avg.isPresent() ? avg.getAsDouble() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Double readMemory(String runDir, String service, String mode) {
        try {
            Path file = Paths.get(runDir, "memory", "memory-" + mode + ".csv");
            if (!Files.exists(file)) return null;
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                return br.lines()
                        .skip(1)
                        .filter(l -> l.startsWith(service + ",idle,"))
                        .map(l -> Double.parseDouble(l.split(",")[3]))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Double readImageSize(String runDir, String service, String mode) {
        try {
            Path file = Paths.get(runDir, "images", "image-sizes-" + mode + ".csv");
            if (!Files.exists(file)) return null;
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                return br.lines()
                        .skip(1)
                        .filter(l -> l.startsWith(service + ","))
                        .map(l -> Double.parseDouble(l.split(",")[2]))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
