package com.groupeisi.loan.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Resilience4jMetricsConfig {

    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }

    @Bean
    public MeterBinder timeLimiterMetrics(TimeLimiterRegistry timeLimiterRegistry) {
        return TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry);
    }
}
