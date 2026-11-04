package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import com.zerocoldstart.dashboard.model.MetricResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final ProjectProperties projectProperties;

    private static final List<String> SERVICES = List.of(
            "catalog-service", "user-service", "loan-service", "library-gateway"
    );

    public Map<String, List<MetricResult>> getAllMetrics() {
        Map<String, List<MetricResult>> results = new HashMap<>();
        results.put("jvm", getMetricsForMode("jvm"));
        results.put("native", getMetricsForMode("native"));
        return results;
    }

    public List<MetricResult> getMetricsForMode(String mode) {
        String latestRun = getLatestRunDir(mode);
        if (latestRun == null) return Collections.emptyList();

        List<MetricResult> results = new ArrayList<>();

        for (String service : SERVICES) {
            MetricResult.MetricResultBuilder builder = MetricResult.builder()
                    .service(service)
                    .mode(mode.toUpperCase());

            // Startup time
            Double startup = readStartupTime(latestRun, service, mode);
            builder.startupMs(startup);

            // Cold start
            Double coldStart = readColdStart(latestRun, service, mode);
            builder.coldStartS(coldStart);

            // Memory
            Double[] memory = readMemory(latestRun, service, mode);
            builder.memoryIdleMi(memory[0]);
            builder.memoryWarmMi(memory[1]);

            // Image size
            Double imageSize = readImageSize(latestRun, service, mode);
            builder.imageSizeMb(imageSize);

            results.add(builder.build());
        }

        // Load test (global)
        Map<String, Double> load = readLoadTest(latestRun, mode);
        if (!results.isEmpty()) {
            results.get(0).setThroughputRps(load.get("throughput_rps"));
            results.get(0).setAvgLatencyMs(load.get("avg_latency_ms"));
        }

        return results;
    }

    public Map<String, Object> getComparisonSummary() {
        List<MetricResult> jvm = getMetricsForMode("jvm");
        List<MetricResult> native_ = getMetricsForMode("native");

        Map<String, Object> summary = new LinkedHashMap<>();

        for (String service : SERVICES) {
            Map<String, Object> svcData = new LinkedHashMap<>();

            Optional<MetricResult> jvmResult = jvm.stream()
                    .filter(m -> m.getService().equals(service))
                    .findFirst();
            Optional<MetricResult> nativeResult = native_.stream()
                    .filter(m -> m.getService().equals(service))
                    .findFirst();

            if (jvmResult.isPresent() && nativeResult.isPresent()) {
                double jvmStartup = jvmResult.get().getStartupMs() != null
                        ? jvmResult.get().getStartupMs() : 0;
                double nativeStartup = nativeResult.get().getStartupMs() != null
                        ? nativeResult.get().getStartupMs() : 0;

                svcData.put("jvmStartupMs", jvmStartup);
                svcData.put("nativeStartupMs", nativeStartup);
                svcData.put("startupGainX", nativeStartup > 0
                        ? Math.round(jvmStartup / nativeStartup * 10.0) / 10.0 : 0);

                double jvmCold = jvmResult.get().getColdStartS() != null
                        ? jvmResult.get().getColdStartS() : 0;
                double nativeCold = nativeResult.get().getColdStartS() != null
                        ? nativeResult.get().getColdStartS() : 0;

                svcData.put("jvmColdStartS", jvmCold);
                svcData.put("nativeColdStartS", nativeCold);
                svcData.put("coldStartGainX", nativeCold > 0
                        ? Math.round(jvmCold / nativeCold * 10.0) / 10.0 : 0);

                double jvmMem = jvmResult.get().getMemoryIdleMi() != null
                        ? jvmResult.get().getMemoryIdleMi() : 0;
                double nativeMem = nativeResult.get().getMemoryIdleMi() != null
                        ? nativeResult.get().getMemoryIdleMi() : 0;

                svcData.put("jvmMemoryMi", jvmMem);
                svcData.put("nativeMemoryMi", nativeMem);
                svcData.put("memoryGainPct", jvmMem > 0
                        ? Math.round((jvmMem - nativeMem) / jvmMem * 100) : 0);
            }

            summary.put(service, svcData);
        }

        return summary;
    }

    private String getLatestRunDir(String mode) {
        try {
            Path resultsPath = Paths.get(projectProperties.getResultsPath());
            if (!Files.exists(resultsPath)) return null;

            return Files.list(resultsPath)
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        Path modeFile = p.resolve(mode).resolve("startup-" + mode + ".csv");
                        Path summaryFile = p.resolve("SUMMARY-" + mode + ".md");
                        return Files.exists(modeFile) || Files.exists(summaryFile);
                    })
                    .sorted(Comparator.reverseOrder())
                    .findFirst()
                    .map(Path::toString)
                    .orElse(null);
        } catch (IOException e) {
            log.error("Error listing results directory", e);
            return null;
        }
    }

    private Double readStartupTime(String runDir, String service, String mode) {
        try {
            Path file = Paths.get(runDir, "startup", "startup-" + mode + ".csv");
            if (!Files.exists(file)) return null;
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                return br.lines()
                        .skip(1)
                        .filter(l -> l.startsWith(service + ","))
                        .map(l -> l.split(",")[1])
                        .map(Double::parseDouble)
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.debug("No startup data for {} {}", service, mode);
            return null;
        }
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
            log.debug("No coldstart data for {} {}", service, mode);
            return null;
        }
    }

    private Double[] readMemory(String runDir, String service, String mode) {
        Double idle = null, warm = null;
        try {
            Path file = Paths.get(runDir, "memory", "memory-" + mode + ".csv");
            if (!Files.exists(file)) return new Double[]{null, null};
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                List<String> lines = br.lines()
                        .skip(1)
                        .filter(l -> l.startsWith(service + ","))
                        .collect(Collectors.toList());
                for (String line : lines) {
                    String[] parts = line.split(",");
                    if (parts[1].equals("idle")) idle = Double.parseDouble(parts[3]);
                    if (parts[1].equals("warm")) warm = Double.parseDouble(parts[3]);
                }
            }
        } catch (Exception e) {
            log.debug("No memory data for {} {}", service, mode);
        }
        return new Double[]{idle, warm};
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
            log.debug("No image size data for {} {}", service, mode);
            return null;
        }
    }

    private Map<String, Double> readLoadTest(String runDir, String mode) {
        Map<String, Double> result = new HashMap<>();
        try {
            Path file = Paths.get(runDir, "load", "load-summary-" + mode + ".csv");
            if (!Files.exists(file)) return result;
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                br.lines().forEach(line -> {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        try {
                            result.put(parts[0], Double.parseDouble(parts[1]));
                        } catch (NumberFormatException ignored) {}
                    }
                });
            }
        } catch (Exception e) {
            log.debug("No load test data for mode {}", mode);
        }
        return result;
    }
}
