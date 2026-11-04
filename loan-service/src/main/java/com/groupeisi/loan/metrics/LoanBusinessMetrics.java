package com.groupeisi.loan.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class LoanBusinessMetrics {

    private final Counter loansCreatedCounter;
    private final Counter outboxEventsCounter;
    private final Counter feignFailuresCounter;

    public LoanBusinessMetrics(MeterRegistry registry) {
        this.loansCreatedCounter = Counter.builder("loan.created.total")
                .description("Nombre de créations d'emprunts")
                .register(registry);
        this.outboxEventsCounter = Counter.builder("loan.outbox.events.total")
                .description("Nombre d'événements Outbox générés")
                .register(registry);
        this.feignFailuresCounter = Counter.builder("loan.feign.failures.total")
                .description("Nombre d'échecs d'appels Feign")
                .register(registry);
    }

    public void incrementLoansCreated() {
        loansCreatedCounter.increment();
    }

    public void incrementOutboxEvents() {
        outboxEventsCounter.increment();
    }

    public void incrementFeignFailures() {
        feignFailuresCounter.increment();
    }
}
