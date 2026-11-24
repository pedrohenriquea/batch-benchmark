package com.tcc.benchmark.service;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Responsavel pela carga inicial e padronizacao do conjunto de dados.
 * Os valores sao gerados de forma deterministica a partir do indice, garantindo
 * que execucoes diferentes do experimento partam exatamente do mesmo estado.
 */
@Service
public class DataLoaderService {

    private static final int INSERT_BATCH = 5_000;

    private final JdbcTemplate jdbcTemplate;

    public DataLoaderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long load(int count) {
        jdbcTemplate.execute("TRUNCATE TABLE registro RESTART IDENTITY");

        final String sql = "INSERT INTO registro (nome, categoria, valor_base, valor, processado) "
                + "VALUES (?, ?, ?, ?, false)";

        int remaining = count;
        int offset = 0;
        while (remaining > 0) {
            final int chunk = Math.min(INSERT_BATCH, remaining);
            final int base = offset;
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = base + i;
                    BigDecimal valorBase = BigDecimal.valueOf(10L + (idx % 1000), 2); // 0.10 .. 10.09
                    ps.setString(1, "registro_" + idx);
                    ps.setString(2, "CAT_" + (idx % 10));
                    ps.setBigDecimal(3, valorBase);
                    ps.setBigDecimal(4, valorBase);
                }

                @Override
                public int getBatchSize() {
                    return chunk;
                }
            });
            offset += chunk;
            remaining -= chunk;
        }
        return count;
    }

    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro", Long.class);
        return total == null ? 0L : total;
    }
}
