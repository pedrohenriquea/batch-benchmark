package com.tcc.benchmark.repository;

import com.tcc.benchmark.domain.Registro;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio da entidade {@link Registro}. A consulta por keyset (id maior que o ultimo
 * processado) evita o custo crescente da paginacao por offset em grandes volumes.
 */
public interface RegistroRepository extends JpaRepository<Registro, Long> {

    Slice<Registro> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
