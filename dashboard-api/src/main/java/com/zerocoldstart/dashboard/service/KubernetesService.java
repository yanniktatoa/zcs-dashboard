package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties.ServiceConfig;
import com.zerocoldstart.dashboard.model.PodInfo;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesService {

    private final KubernetesClient client;
    private final KubernetesProperties kubernetesProperties;

    public List<PodInfo> getAllPods() {
        return client.pods()
                .inNamespace(kubernetesProperties.getNamespace())
                .list()
                .getItems()
                .stream()
                .map(this::mapToPodInfo)
                .toList();
    }

    public void switchMode(String serviceName, String mode) {
        String image = getImageForMode(serviceName, mode);
        log.info("Switching {} to {} mode with image {}", serviceName, mode, image);

        client.apps().deployments()
                .inNamespace(kubernetesProperties.getNamespace())
                .withName(serviceName)
                .edit(d -> {
                    d.getSpec().getTemplate().getSpec()
                            .getContainers().get(0).setImage(image);
                    return d;
                });
    }

    public void scaleDeployment(String serviceName, int replicas) {
        log.info("Scaling {} to {} replicas", serviceName, replicas);
        client.apps().deployments()
                .inNamespace(kubernetesProperties.getNamespace())
                .withName(serviceName)
                .scale(replicas);
    }

    public void restartDeployment(String serviceName) {
        log.info("Restarting deployment {}", serviceName);
        client.apps().deployments()
                .inNamespace(kubernetesProperties.getNamespace())
                .withName(serviceName)
                .rolling().restart();
    }

    public String getPodLogs(String serviceName, int tailLines) {
        return client.pods()
                .inNamespace(kubernetesProperties.getNamespace())
                .withLabel("app", serviceName)
                .list().getItems().stream()
                .findFirst()
                .map(pod -> client.pods()
                        .inNamespace(kubernetesProperties.getNamespace())
                        .withName(pod.getMetadata().getName())
                        .tailingLines(tailLines)
                        .getLog())
                .orElse("No logs available");
    }

    public String getDeploymentMode(String serviceName) {
        try {
            var deployment = client.apps().deployments()
                    .inNamespace(kubernetesProperties.getNamespace())
                    .withName(serviceName)
                    .get();
            if (deployment == null) return "UNKNOWN";
            String image = deployment.getSpec().getTemplate()
                    .getSpec().getContainers().get(0).getImage();
            return image.contains(":native") ? "NATIVE" : "JVM";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Decouvre dynamiquement tous les deployments du namespace
     * et detecte leur mode JVM/Native via le tag de l'image.
     * Retourne uniquement les deployments applicatifs (pas les infra MySQL, Redis, etc.)
     */
    public List<Map<String, Object>> discoverServices() {
        return client.apps().deployments()
                .inNamespace(kubernetesProperties.getNamespace())
                .list().getItems().stream()
                .filter(d -> {
                    String image = d.getSpec().getTemplate().getSpec()
                            .getContainers().get(0).getImage();
                    // Exclure les images d'infrastructure connues
                    return !image.contains("mysql") && !image.contains("redis")
                            && !image.contains("kafka") && !image.contains("zookeeper")
                            && !image.contains("zipkin") && !image.contains("confluentinc")
                            && !image.contains("openzipkin");
                })
                .map(d -> {
                    String name = d.getMetadata().getName();
                    String image = d.getSpec().getTemplate().getSpec()
                            .getContainers().get(0).getImage();
                    String mode = image.contains(":native") ? "NATIVE" : "JVM";

                    // Detecter le port depuis les containerPorts
                    int port = 8080;
                    var ports = d.getSpec().getTemplate().getSpec()
                            .getContainers().get(0).getPorts();
                    if (ports != null && !ports.isEmpty()) {
                        port = ports.get(0).getContainerPort();
                    }

                    // Detecter les images JVM/Native disponibles
                    String jvmImage = image.contains(":native")
                            ? image.replace(":native", ":jvm") : image;
                    String nativeImage = image.contains(":jvm")
                            ? image.replace(":jvm", ":native")
                            : image.contains(":") ? image.replace(image.substring(image.lastIndexOf(":")), ":native")
                            : image + ":native";

                    Map<String, Object> svc = new java.util.LinkedHashMap<>();
                    svc.put("name", name);
                    svc.put("image", image);
                    svc.put("mode", mode);
                    svc.put("port", port);
                    svc.put("jvmImage", jvmImage);
                    svc.put("nativeImage", nativeImage);
                    svc.put("replicas", d.getSpec().getReplicas());
                    return svc;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<String, String> getClusterInfo() {
        var version = client.getKubernetesVersion();
        var nodes = client.nodes().list().getItems();
        return Map.of(
                "version", version.getGitVersion(),
                "nodes", String.valueOf(nodes.size()),
                "namespace", kubernetesProperties.getNamespace()
        );
    }

    private PodInfo mapToPodInfo(Pod pod) {
        String podName = pod.getMetadata().getName();
        String image = pod.getSpec().getContainers().get(0).getImage();
        String mode = image.contains(":native") ? "NATIVE" : "JVM";
        String status = pod.getStatus().getPhase();
        int restarts = pod.getStatus().getContainerStatuses() != null
                && !pod.getStatus().getContainerStatuses().isEmpty()
                ? pod.getStatus().getContainerStatuses().get(0).getRestartCount()
                : 0;
        boolean ready = pod.getStatus().getContainerStatuses() != null
                && !pod.getStatus().getContainerStatuses().isEmpty()
                && Boolean.TRUE.equals(pod.getStatus().getContainerStatuses().get(0).getReady());

        String age = computeAge(pod.getMetadata().getCreationTimestamp());
        String service = pod.getMetadata().getLabels() != null
                ? pod.getMetadata().getLabels().getOrDefault("app", podName)
                : podName;

        return PodInfo.builder()
                .name(podName)
                .service(service)
                .status(status)
                .image(image)
                .mode(mode)
                .restarts(restarts)
                .age(age)
                .ready(ready)
                .build();
    }

    private String getImageForMode(String serviceName, String mode) {
        return kubernetesProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .map(s -> mode.equals("NATIVE") ? s.getNativeImage() : s.getJvmImage())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceName));
    }

    private String computeAge(String creationTimestamp) {
        try {
            OffsetDateTime created = OffsetDateTime.parse(creationTimestamp);
            Duration duration = Duration.between(created, OffsetDateTime.now());
            if (duration.toDays() > 0) return duration.toDays() + "d";
            if (duration.toHours() > 0) return duration.toHours() + "h";
            return duration.toMinutes() + "m";
        } catch (Exception e) {
            return "N/A";
        }
    }
}
