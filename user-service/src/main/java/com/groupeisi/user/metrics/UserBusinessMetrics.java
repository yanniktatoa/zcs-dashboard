package com.groupeisi.user.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class UserBusinessMetrics {

    private final Counter usersCreatedCounter;
    private final Counter deletionsRefusedCounter;
    private final Counter duplicateEmailsCounter;

    public UserBusinessMetrics(MeterRegistry registry) {
        this.usersCreatedCounter = Counter.builder("user.created.total")
                .description("Nombre d'utilisateurs créés")
                .register(registry);
        this.deletionsRefusedCounter = Counter.builder("user.deletion.refused.total")
                .description("Suppressions refusées (emprunts actifs)")
                .register(registry);
        this.duplicateEmailsCounter = Counter.builder("user.email.duplicate.total")
                .description("Tentatives de création avec email dupliqué")
                .register(registry);
    }

    public void incrementUsersCreated() {
        usersCreatedCounter.increment();
    }

    public void incrementDeletionsRefused() {
        deletionsRefusedCounter.increment();
    }

    public void incrementDuplicateEmails() {
        duplicateEmailsCounter.increment();
    }
}
