package com.tcc.benchmark.service;

import com.tcc.benchmark.domain.Registro;
import com.tcc.benchmark.repository.RegistroRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Estrategia 1 - JPA (ORM).
 * Percorre os registros por keyset, modifica as entidades gerenciadas e delega a
 * persistencia ao provedor (Hibernate), que agrupa os UPDATEs conforme
 * {@code hibernate.jdbc.batch_size}. O flush/clear periodico mantem o contexto de
 * persistencia enxuto, evitando consumo crescente de memoria.
 */
@Service
public class JpaBatchService {

    private final RegistroRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaBatchService(RegistroRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public long process(int chunkSize, BigDecimal fator) {
        final Instant now = Instant.now();
        long lastId = 0L;
        long total = 0L;

        while (true) {
            Slice<Registro> slice =
                    repository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, chunkSize));
            List<Registro> content = slice.getContent();
            if (content.isEmpty()) {
                break;
            }
            for (Registro r : content) {
                r.setValor(r.getValorBase().multiply(fator));
                r.setProcessado(true);
                r.setAtualizadoEm(now);
            }
            repository.saveAll(content);
            entityManager.flush();
            entityManager.clear();

            lastId = content.get(content.size() - 1).getId();
            total += content.size();
            if (content.size() < chunkSize) {
                break;
            }
        }
        return total;
    }
}
