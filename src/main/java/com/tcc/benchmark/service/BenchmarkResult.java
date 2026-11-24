package com.tcc.benchmark.service;

/**
 * Resultado quantitativo de uma execucao do processamento batch.
 */
public record BenchmarkResult(
        String strategy,
        long totalRecords,
        int chunkSize,
        double durationMs,
        double throughputPerSec) {
}
