package com.groupeisi.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du CorrelationIdFilter.
 *
 * Stratégie : on instancie le filtre directement (pas de contexte Spring)
 * et on vérifie son comportement en isolation via MockServerWebExchange.
 *
 * Intérêt mémoire : démontre la testabilité des filtres WebFlux sans démarrer
 * le serveur complet, en exploitant la nature déclarative du modèle réactif.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldGenerateCorrelationIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/books/1").build()
        );
        AtomicReference<String> capturedId = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            capturedId.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedId.get())
                .as("Un UUID doit être généré si X-Correlation-Id est absent")
                .isNotNull()
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldPreserveExistingCorrelationId() {
        String existingId = "frontend-trace-abc-123";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/42")
                        .header(CorrelationIdFilter.CORRELATION_ID, existingId)
                        .build()
        );
        AtomicReference<String> capturedId = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            capturedId.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedId.get())
                .as("L'ID existant doit être conservé, pas remplacé")
                .isEqualTo(existingId);
    }

    @Test
    void shouldAddCorrelationIdToResponseHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/loans/1").build()
        );
        // Le chain appelle setComplete() sur le decorated response,
        // déclenchant injectHeader() dans le ServerHttpResponseDecorator
        GatewayFilterChain chain = ex -> ex.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        // getHeaders() du decorator délègue à l'original MockServerHttpResponse
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID))
                .as("X-Correlation-Id doit être présent dans la réponse")
                .isNotNull()
                .isNotBlank();
    }

    @Test
    void shouldRejectBlankCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/books")
                        .header(CorrelationIdFilter.CORRELATION_ID, "   ")
                        .build()
        );
        AtomicReference<String> capturedId = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            capturedId.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedId.get())
                .as("Un ID vide/blanc doit être remplacé par un UUID généré")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
