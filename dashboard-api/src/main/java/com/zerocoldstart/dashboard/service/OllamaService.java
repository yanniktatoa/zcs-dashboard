package com.zerocoldstart.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class OllamaService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3.2:3b";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String analyze(String serviceName, String logContent) {
        String prompt = buildPrompt(serviceName, logContent);
        try {
            String body = String.format(
                "{\"model\":\"%s\",\"prompt\":%s,\"stream\":false,\"options\":{\"temperature\":0.3,\"num_predict\":800}}",
                MODEL,
                jsonEscape(prompt)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractResponse(response.body());
            }
            return "Ollama indisponible (status " + response.statusCode() + ")";
        } catch (Exception e) {
            log.error("Ollama call failed", e);
            return "Erreur Ollama : " + e.getMessage();
        }
    }

    public Flux<String> analyzeStream(String serviceName, String logContent) {
        String prompt = buildPrompt(serviceName, logContent);
        return Flux.create(emitter -> {
            try {
                String body = String.format(
                    "{\"model\":\"%s\",\"prompt\":%s,\"stream\":true,\"options\":{\"temperature\":0.3,\"num_predict\":800}}",
                    MODEL,
                    jsonEscape(prompt)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
                        .body()
                        .forEach(line -> {
                            if (!line.isBlank()) {
                                String token = extractToken(line);
                                if (token != null && !token.isEmpty()) {
                                    emitter.next(token);
                                }
                                if (line.contains("\"done\":true")) {
                                    emitter.complete();
                                }
                            }
                        });
                emitter.complete();
            } catch (Exception e) {
                log.error("Ollama stream failed", e);
                emitter.error(e);
            }
        });
    }

    private String buildPrompt(String serviceName, String logContent) {
        return String.format(
            "Tu es un assistant technique pour GraalVM Native Image.\n" +
            "Le service '%s' a des problemes de compilation.\n\n" +
            "Voici les erreurs detectees :\n%s\n\n" +
            "Explique en 2-3 phrases simples ce que signifient ces erreurs " +
            "et donne UNE solution concrete et specifique pour chacune.\n\n" +
            "Reponds en francais, de facon concise et precise.",
            serviceName, logContent
        );
    }

    private String extractResponse(String json) {
        try {
            int idx = json.indexOf("\"response\":\"");
            if (idx < 0) return json;
            int start = idx + 12;
            int end = json.indexOf("\"", start);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf("\"", end + 1);
            }
            return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"");
        } catch (Exception e) {
            return json;
        }
    }

    private String extractToken(String json) {
        try {
            int idx = json.indexOf("\"response\":\"");
            if (idx < 0) return null;
            int start = idx + 12;
            int end = json.indexOf("\"", start);
            if (end < 0) return null;
            return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"");
        } catch (Exception e) {
            return null;
        }
    }

    private String jsonEscape(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
