package com.tcc.benchmark.service;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Estrategia 2 - SQL nativo com batch update (foco do estudo).
 * Le os identificadores por keyset e aplica os UPDATEs em lote via
 * {@link JdbcTemplate#batchUpdate}, sem a camada de mapeamento objeto-relacional.
 * Com {@code reWriteBatchedInserts=true} na URL JDBC, o driver PostgreSQL reescreve
 * o lote em poucas idas ao banco.
 */
@Service
public class NativeSqlBatchService {

    private static final String SELECT_IDS =
            "SELECT id FROM registro WHERE id > ? ORDER BY id ASC LIMIT ?";
    private static final String UPDATE_ROW =
            "UPDATE registro SET valor = valor_base * ?, processado = true, atualizado_em = ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public NativeSqlBatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long process(int chunkSize, BigDecimal fator) {
        final Timestamp now = Timestamp.from(Instant.now());
        long lastId = 0L;
        long total = 0L;

        while (true) {
            List<Long> ids = jdbcTemplate.query(
                    SELECT_IDS,
                    (rs, rowNum) -> rs.getLong(1),
                    lastId, chunkSize);
            if (ids.isEmpty()) {
                break;
            }
            jdbcTemplate.batchUpdate(UPDATE_ROW, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setBigDecimal(1, fator);
                    ps.setTimestamp(2, now);
                    ps.setLong(3, ids.get(i));
                }

                @Override
                public int getBatchSize() {
                    return ids.size();
                }
            });

            lastId = ids.get(ids.size() - 1);
            total += ids.size();
            if (ids.size() < chunkSize) {
                break;
            }
        }
        return total;
    }
}
