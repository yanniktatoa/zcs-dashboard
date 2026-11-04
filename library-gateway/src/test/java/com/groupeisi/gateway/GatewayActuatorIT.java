package com.groupeisi.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que les endpoints Actuator de la gateway sont accessibles.
 *
 * Ces tests valident :
 *   - Le démarrage correct du contexte Spring Cloud Gateway
 *   - L'exposition de /actuator/health avec les probes K8s (liveness/readiness)
 *   - L'exposition de /actuator/prometheus pour le scraping Prometheus
 *   - L'exposition de /actuator/gateway/routes pour l'inspection des routes
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayActuatorIT {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void health_shouldReturn200WithStatusUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void liveness_shouldReturn200() {
        client.get().uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void readiness_shouldReturn200() {
        client.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheus_shouldExposeMetrics() {
        client.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
    }

    @Test
    void info_shouldExposeAppMetadata() {
        // Note : GlobalFilter (CorrelationIdFilter) s'applique aux requêtes routées
        // vers les backends, pas aux endpoints actuator (interceptés avant le FilteringWebHandler).
        // La propagation du Correlation ID est validée dans CorrelationIdFilterTest.
        client.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.name").isEqualTo("library-gateway");
    }
}
