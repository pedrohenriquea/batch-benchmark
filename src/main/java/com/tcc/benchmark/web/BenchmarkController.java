package com.tcc.benchmark.web;

import com.tcc.benchmark.service.BenchmarkResult;
import com.tcc.benchmark.service.BenchmarkService;
import com.tcc.benchmark.service.DataLoaderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints para disparar a carga de dados e a execucao dos experimentos.
 */
@RestController
public class BenchmarkController {

    private final DataLoaderService dataLoaderService;
    private final BenchmarkService benchmarkService;

    public BenchmarkController(DataLoaderService dataLoaderService, BenchmarkService benchmarkService) {
        this.dataLoaderService = dataLoaderService;
        this.benchmarkService = benchmarkService;
    }

    /** Carga inicial: trunca a tabela e insere {@code count} registros padronizados. */
    @PostMapping("/api/data/load")
    public Map<String, Object> load(@RequestParam(defaultValue = "100000") int count) {
        long start = System.nanoTime();
        long loaded = dataLoaderService.load(count);
        double loadMs = (System.nanoTime() - start) / 1_000_000.0;
        return Map.of("count", loaded, "loadMs", loadMs);
    }

    @GetMapping("/api/data/count")
    public Map<String, Object> count() {
        return Map.of("count", dataLoaderService.count());
    }

    /** Executa o processamento batch para a estrategia informada e devolve as metricas. */
    @PostMapping("/api/benchmark/run")
    public BenchmarkResult run(@RequestParam String strategy,
                               @RequestParam(defaultValue = "1000") int chunkSize) {
        return benchmarkService.run(strategy, chunkSize);
    }
}
