package com.example.minishop.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PipelineMetrics {

    private final Counter produced;
    private final Counter consumed;
    private final Counter failed;

    public PipelineMetrics(MeterRegistry registry) {
        this.produced = Counter.builder("minishop.pipeline.produced")
                .description("Events successfully published to Kafka")
                .register(registry);
        this.consumed = Counter.builder("minishop.pipeline.consumed")
                .description("Events successfully consumed from Kafka")
                .register(registry);
        this.failed = Counter.builder("minishop.pipeline.failed")
                .description("Events that failed in a consumer and were retried")
                .register(registry);
    }

    public void recordProduced() { produced.increment(); }
    public void recordConsumed() { consumed.increment(); }
    public void recordFailed()   { failed.increment(); }
}