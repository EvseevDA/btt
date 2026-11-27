package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Читает справочник видов транспорта. */
public interface TransportKindRepository extends JpaRepository<TransportKind, UUID> {
    /** Возвращает страницу видов транспорта по названию. */
    List<TransportKind> findAllByOrderByNameAscIdAsc(Pageable page);
}
