package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrometheusService {

    private final KubernetesProperties kubernetesProperties;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private String prometheusUrl = "http://localhost:9090";

    public void setPrometheusUrl(String url) {
        this.prometheusUrl = url;
    }

    public Map<String, Object> getMetrics(String serviceName) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("service", serviceName);

        Double cpu = queryCpu(serviceName);
        Double mem = queryMemory(serviceName);

        if (cpu == null || mem == null) {
            Map<String, Double> kubectlMetrics = getKubectlTopMetrics(serviceName);
            cpu = kubectlMetrics.getOrDefault("cpu", null);
            mem = kubectlMetrics.getOrDefault("memory", null);
            metrics.put("source", "kubectl-top");
        } else {
            metrics.put("source", "prometheus");
        }

        metrics.put("cpu", cpu);
        metrics.put("memory", mem);
        metrics.put("requestRate", queryRequestRate(serviceName));
        metrics.put("errorRate", queryErrorRate(serviceName));
        metrics.put("p99Latency", queryP99(serviceName));
        return metrics;
    }

    private Map<String, Double> getKubectlTopMetrics(String serviceName) {
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        try {
            Process process = new ProcessBuilder("bash", "-c",
                    String.format("kubectl top pod -n %s -l app=%s --no-headers 2>/dev/null | head -1",
                            kubernetesProperties.getNamespace(), serviceName))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();

            if (!output.isEmpty()) {
                String[] parts = output.split("\\s+");
                if (parts.length >= 3) {
                    String cpuStr = parts[1].replace("m", "");
                    String memStr = parts[2].replace("Mi", "").replace("Ki", "");
                    result.put("cpu", Double.parseDouble(cpuStr));
                    result.put("memory", Double.parseDouble(memStr));
                }
            }
        } catch (Exception e) {
            log.debug("kubectl top failed for {}: {}", serviceName, e.getMessage());
        }
        return result;
    }

    public List<Map<String, Object>> getAllServicesMetrics() {
        List<String> services = List.of(
                "catalog-service", "user-service", "loan-service", "library-gateway"
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (String svc : services) {
            result.add(getMetrics(svc));
        }
        return result;
    }

    private Double queryCpu(String serviceName) {
        String query = String.format(
                "sum(rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=~\"%s.*\"}[2m]))*1000",
                kubernetesProperties.getNamespace(), serviceName);
        return queryScalar(query);
    }

    private Double queryMemory(String serviceName) {
        String query = String.format(
                "sum(container_memory_working_set_bytes{namespace=\"%s\",pod=~\"%s.*\"})/1048576",
                kubernetesProperties.getNamespace(), serviceName);
        return queryScalar(query);
    }

    private Double queryRequestRate(String serviceName) {
        String query = String.format(
                "sum(rate(http_server_requests_seconds_count{namespace=\"%s\",pod=~\"%s.*\"}[1m]))",
                kubernetesProperties.getNamespace(), serviceName);
        return queryScalar(query);
    }

    private Double queryErrorRate(String serviceName) {
        String query = String.format(
                "sum(rate(http_server_requests_seconds_count{namespace=\"%s\",pod=~\"%s.*\",status=~\"5..\"}[1m]))",
                kubernetesProperties.getNamespace(), serviceName);
        return queryScalar(query);
    }

    private Double queryP99(String serviceName) {
        String query = String.format(
                "histogram_quantile(0.99,sum(rate(http_server_requests_seconds_bucket{namespace=\"%s\",pod=~\"%s.*\"}[1m]))by(le))*1000",
                kubernetesProperties.getNamespace(), serviceName);
        return queryScalar(query);
    }

    private Double queryScalar(String promQuery) {
        try {
            String url = prometheusUrl + "/api/v1/query?query="
                    + java.net.URLEncoder.encode(promQuery, java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                String body = resp.body();
                int resultIdx = body.indexOf("\"result\":[{");
                if (resultIdx > 0) {
                    int valueIdx = body.indexOf("\"value\":[", resultIdx);
                    if (valueIdx > 0) {
                        int numStart = body.indexOf(",\"", valueIdx + 9) + 2;
                        int numEnd = body.indexOf("\"", numStart);
                        String numStr = body.substring(numStart, numEnd);
                        return Math.round(Double.parseDouble(numStr) * 100.0) / 100.0;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Prometheus query failed for: {}", promQuery);
        }
        return null;
    }
}
