package com.groupeisi.gateway.filter;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Filtre global qui garantit que chaque requête possède un identifiant de corrélation.
 *
 * Comportement :
 *   - Si X-Correlation-Id est présent dans la requête entrante, il est conservé.
 *   - Sinon, un UUID est généré et injecté.
 *   - L'identifiant est propagé aux microservices aval ET renvoyé dans la réponse client.
 *
 * Implémentation : ServerHttpResponseDecorator intercèpte writeWith / writeAndFlushWith /
 * setComplete pour injecter le header avant tout envoi, qu'il s'agisse d'une réponse
 * proxiée (backend) ou d'une réponse directe (actuator).
 *
 * Intérêt mémoire : le Correlation ID est fondamental pour relier les spans de tracing
 * distribué (Zipkin) à travers la gateway et les microservices, sans couplage entre services.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID = "X-Correlation-Id";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        ServerHttpResponse decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {

            private void injectHeader() {
                getHeaders().addIfAbsent(CORRELATION_ID, correlationId);
            }

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                injectHeader();
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                injectHeader();
                return super.writeAndFlushWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                injectHeader();
                return super.setComplete();
            }
        };

        return chain.filter(
                exchange.mutate()
                        .request(r -> r.header(CORRELATION_ID, correlationId))
                        .response(decorated)
                        .build()
        );
    }
}
