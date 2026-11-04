package com.groupeisi.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtre global de logging : trace chaque requête entrante et la réponse associée.
 *
 * Format :
 *   --> GET /api/users/1 [correlationId=abc-123]
 *   <-- GET /api/users/1 200 OK 42ms [correlationId=abc-123]
 *
 * S'exécute après CorrelationIdFilter (HIGHEST_PRECEDENCE + 1) pour que
 * le Correlation ID soit déjà présent dans le header de la requête mutée.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startMs = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getPath().value();
        String corrId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID);

        log.info("--> {} {} [correlationId={}]", method, path, corrId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long elapsed = System.currentTimeMillis() - startMs;
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            log.info("<-- {} {} {} {}ms [correlationId={}]", method, path, status, elapsed, corrId);
        }));
    }
}
