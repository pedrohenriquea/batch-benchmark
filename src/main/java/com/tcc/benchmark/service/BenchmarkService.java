package com.tcc.benchmark.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orquestra a execucao das estrategias e registra as metricas de desempenho no Micrometer,
 * tornando-as disponiveis para coleta pelo Prometheus e visualizacao no Grafana.
 *
 * Metricas expostas:
 *  - batch_processing_seconds (Timer, tag strategy): distribuicao do tempo de processamento;
 *  - batch_last_throughput     (Gauge, tag strategy): throughput da ultima execucao (reg/s);
 *  - batch_last_duration_ms    (Gauge, tag strategy): duracao da ultima execucao (ms).
 */
@Service
public class BenchmarkService {

    /** Fator de reajuste aplicado ao valor_base; deterministico e identico entre estrategias. */
    private static final BigDecimal FATOR = new BigDecimal("1.10");

    private final JpaBatchService jpaBatchService;
    private final NativeSqlBatchService nativeSqlBatchService;
    private final NativeBulkService nativeBulkService;
    private final MeterRegistry registry;

    private final Map<String, AtomicReference<Double>> throughputGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> durationGauges = new ConcurrentHashMap<>();

    public BenchmarkService(JpaBatchService jpaBatchService,
                            NativeSqlBatchService nativeSqlBatchService,
                            NativeBulkService nativeBulkService,
                            MeterRegistry registry) {
        this.jpaBatchService = jpaBatchService;
        this.nativeSqlBatchService = nativeSqlBatchService;
        this.nativeBulkService = nativeBulkService;
        this.registry = registry;
    }

    public BenchmarkResult run(String strategy, int chunkSize) {
        long start = System.nanoTime();
        long total = switch (strategy) {
            case "jpa" -> jpaBatchService.process(chunkSize, FATOR);
            case "native" -> nativeSqlBatchService.process(chunkSize, FATOR);
            case "native-bulk" -> nativeBulkService.process(FATOR);
            default -> throw new IllegalArgumentException(
                    "Estrategia invalida: " + strategy + " (use jpa | native | native-bulk)");
        };
        long elapsedNanos = System.nanoTime() - start;

        Timer.builder("batch.processing")
                .tag("strategy", strategy)
                .description("Tempo de processamento do batch por estrategia")
                .register(registry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

        double durationMs = elapsedNanos / 1_000_000.0;
        double seconds = elapsedNanos / 1_000_000_000.0;
        double throughput = seconds > 0 ? total / seconds : 0.0;

        gauge(throughputGauges, "batch.last.throughput", strategy).set(throughput);
        gauge(durationGauges, "batch.last.duration.ms", strategy).set(durationMs);

        return new BenchmarkResult(strategy, total, chunkSize, durationMs, throughput);
    }

    private AtomicReference<Double> gauge(Map<String, AtomicReference<Double>> store,
                                          String meterName, String strategy) {
        return store.computeIfAbsent(strategy, s -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            Gauge.builder(meterName, ref, AtomicReference::get)
                    .tag("strategy", s)
                    .register(registry);
            return ref;
        });
    }
}
