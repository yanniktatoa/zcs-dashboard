package com.groupeisi.catalog.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CatalogBusinessMetrics {

    private final Counter loansCounter;
    private final Counter returnsCounter;
    private final Counter optimisticLockConflictsCounter;

    public CatalogBusinessMetrics(MeterRegistry registry) {
        this.loansCounter = Counter.builder("catalog.loans.total")
                .description("Nombre d'emprunts traités (Kafka loan-created)")
                .register(registry);
        this.returnsCounter = Counter.builder("catalog.returns.total")
                .description("Nombre de retours traités (Kafka loan-returned)")
                .register(registry);
        this.optimisticLockConflictsCounter = Counter.builder("catalog.stock.optimistic_lock.conflicts.total")
                .description("Conflits de verrou optimiste sur le stock")
                .register(registry);
    }

    public void incrementLoans() {
        loansCounter.increment();
    }

    public void incrementReturns() {
        returnsCounter.increment();
    }

    public void incrementOptimisticLockConflicts() {
        optimisticLockConflictsCounter.increment();
    }
}
