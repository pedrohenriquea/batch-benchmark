package com.tcc.benchmark.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Estrategia 3 (bonus) - SQL nativo set-based.
 * Atualiza todos os registros em uma unica instrucao, representando o limite superior
 * de otimizacao quando a operacao pode ser expressa de forma puramente declarativa no banco.
 * Util para dimensionar o teto de desempenho frente as abordagens linha-a-linha.
 */
@Service
public class NativeBulkService {

    private static final String UPDATE_ALL =
            "UPDATE registro SET valor = valor_base * ?, processado = true, atualizado_em = ?";

    private final JdbcTemplate jdbcTemplate;

    public NativeBulkService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long process(BigDecimal fator) {
        return jdbcTemplate.update(UPDATE_ALL, fator, Timestamp.from(Instant.now()));
    }
}
