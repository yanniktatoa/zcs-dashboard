package com.zerocoldstart.dashboard.service;

import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import com.zerocoldstart.dashboard.model.BuildRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ProjectProperties projectProperties;

    public List<BuildRecommendation> analyzeLastBuild(String serviceName) {
        String logContent = readLastBuildLog(serviceName);
        if (logContent == null) return List.of();
        return analyze(serviceName, logContent);
    }

    public List<BuildRecommendation> analyzeFromLog(String serviceName, String logContent) {
        return analyze(serviceName, logContent);
    }

    private List<BuildRecommendation> analyze(String serviceName, String log) {
        List<BuildRecommendation> recommendations = new ArrayList<>();

        // Memoire insuffisante
        if (log.contains("Please ensure more than") || log.contains("GC overhead")) {
            String memStr = extractPattern(log, "more than ([\\d.]+)GB");
            double requiredGb = memStr != null ? Double.parseDouble(memStr) : 8.0;
            double recommended = requiredGb + 2.0;
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("MEMORY")
                    .severity("WARNING")
                    .description("Memoire insuffisante pour la compilation native — GC overhead detecte")
                    .currentValue("-J-Xmx" + (int)requiredGb + "g")
                    .recommendedValue("-J-Xmx" + (int)recommended + "g")
                    .buildArgToAdd("-J-Xmx" + (int)recommended + "g")
                    .explanation("GraalVM a besoin de " + requiredGb + " GB. Augmenter a " + recommended
                            + " GB reduit les GC pauses et accelere la compilation d'environ 30%.")
                    .build());
        }

        // Deadlock watchdog
        if (log.contains("watchdog detected no activity") || log.contains("deadlock")) {
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("DEADLOCK")
                    .severity("CRITICAL")
                    .description("Deadlock detecte a l'etape de compilation — build interrompu")
                    .currentValue("-H:DeadlockWatchdogInterval=10")
                    .recommendedValue("-H:DeadlockWatchdogInterval=60")
                    .buildArgToAdd("-H:DeadlockWatchdogInterval=60")
                    .explanation("Le watchdog tue le build apres 10 min d'inactivite (faux positif sur WSL2). "
                            + "Passer a 60 min permet d'eviter les interruptions sur des etapes longues "
                            + "comme [6/8] Compiling methods.")
                    .build());
        }

        // Reduction threads sur WSL
        if (log.contains("12 thread(s)") || log.contains("16 thread(s)")) {
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("MEMORY")
                    .severity("INFO")
                    .description("Trop de threads de compilation — saturation memoire probable sur WSL2")
                    .currentValue("auto-detected (12-16 threads)")
                    .recommendedValue("-J-XX:ActiveProcessorCount=4")
                    .buildArgToAdd("-J-XX:ActiveProcessorCount=4")
                    .explanation("Chaque thread consomme ~500MB. Sur WSL2 avec 12GB alloues, "
                            + "limiter a 4 threads reduit la consommation memoire de ~4GB "
                            + "et evite les crashes OOM a l'etape [6/8].")
                    .build());
        }

        // Reflexion manquante — extraction generique du nom de classe depuis le log
        if (log.contains("MissingReflectionRegistrationError")) {
            String clazz = extractPattern(log,
                    "reflectively access (?:the method |class )?(\\S+?)(?:\\(|\\s|$)");
            if (clazz == null) clazz = extractPattern(log, "without it being registered.*?\\[(\\S+?),");
            String classLabel = clazz != null ? clazz : "YourClass";
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("REFLECTION")
                    .severity("CRITICAL")
                    .description("Classe non enregistree pour la reflexion native : " + classLabel)
                    .currentValue("Non enregistree pour la reflexion AOT")
                    .recommendedValue("@RegisterReflectionForBinding ou RuntimeHintsRegistrar")
                    .buildArgToAdd(null)
                    .explanation("Ajouter @RegisterReflectionForBinding({" + classLabel + ".class}) "
                            + "sur le controller ou service concerne. "
                            + "Pour les DTOs Jackson, ajouter @JsonAutoDetect(fieldVisibility = ANY). "
                            + "Cette erreur est independante du projet et survient sur toute classe "
                            + "accedee par reflexion sans enregistrement AOT explicite.")
                    .build());
        }

        // Proxy dynamique manquant — generique pour tout FeignClient ou proxy JDK
        if (log.contains("dynamic-proxy") || log.contains("MissingReflectionRegistrationError")
                && log.contains("proxy")) {
            String iface = extractPattern(log, "inheriting \\[(\\S+?),");
            String ifaceLabel = iface != null ? iface : "YourFeignClient";
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("PROXY")
                    .severity("CRITICAL")
                    .description("Proxy JDK dynamique non enregistre : " + ifaceLabel)
                    .currentValue("Proxy non declare pour la compilation AOT")
                    .recommendedValue("RuntimeHintsRegistrar.proxies().registerJdkProxy(...)")
                    .buildArgToAdd(null)
                    .explanation("Dans la classe principale @SpringBootApplication, implementer "
                            + "RuntimeHintsRegistrar et appeler hints.proxies().registerJdkProxy("
                            + ifaceLabel + ".class, SpringProxy.class, Advised.class, DecoratingProxy.class). "
                            + "Concerne tous les clients Feign, interfaces @Transactional et proxies Spring AOP.")
                    .build());
        }

        // Log4j2 incompatible — detection generique via l'exception GraalVM
        if ((log.contains("log4j") || log.contains("Log4j"))
                && (log.contains("NoSuchMethodException") || log.contains("ExceptionInInitializerError")
                || log.contains("ClassNotFoundException"))) {
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("LOGGING")
                    .severity("CRITICAL")
                    .description("Framework de logging incompatible avec GraalVM Native Image")
                    .currentValue("Log4j2 (@Log4j2 / spring-boot-starter-log4j2)")
                    .recommendedValue("Logback via SLF4J (@Slf4j — natif Spring Boot)")
                    .buildArgToAdd(null)
                    .explanation("Log4j2 utilise des mecanismes de reflexion et d'initialisation statique "
                            + "incompatibles avec la compilation AOT. Solution universelle : "
                            + "remplacer @Log4j2 par @Slf4j dans toutes les classes, "
                            + "supprimer spring-boot-starter-log4j2 du pom.xml. "
                            + "S'applique a tout projet Spring Boot migre en natif.")
                    .build());
        }

        // GC Serial recommande pour petits services
        if (!log.contains("--gc=G1") && log.contains("Native Image")) {
            recommendations.add(BuildRecommendation.builder()
                    .serviceName(serviceName)
                    .category("MEMORY")
                    .severity("INFO")
                    .description("GC par defaut non optimal pour les services avec peu de memoire")
                    .currentValue("GC par defaut (Serial)")
                    .recommendedValue("--gc=G1 pour environnement contraint")
                    .buildArgToAdd("--gc=G1")
                    .explanation("Le GC G1 reduit les pauses et evite les OOM sur WSL2. "
                            + "A utiliser avec -J-Xmx8g pour des builds stables.")
                    .build());
        }

        recommendations.sort(Comparator.comparing(r ->
                switch (r.getSeverity()) {
                    case "CRITICAL" -> 0;
                    case "WARNING" -> 1;
                    default -> 2;
                }
        ));

        return recommendations;
    }

    private String readLastBuildLog(String serviceName) {
        try {
            Path rawDir = Paths.get(projectProperties.getResultsPath());
            if (!Files.exists(rawDir)) return null;
            return Files.list(rawDir)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .findFirst()
                    .map(dir -> {
                        Path logFile = dir.resolve("raw").resolve("build-" + serviceName + ".log");
                        if (!Files.exists(logFile)) return null;
                        try { return Files.readString(logFile); }
                        catch (Exception e) { return null; }
                    })
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractPattern(String text, String regex) {
        var matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
