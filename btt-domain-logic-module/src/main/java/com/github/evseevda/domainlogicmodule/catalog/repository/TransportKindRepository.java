package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKindEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Читает справочник видов транспорта. */
public interface TransportKindRepository extends JpaRepository<TransportKindEntity, UUID> {
    /** Возвращает страницу видов транспорта по названию. */
    List<TransportKindEntity> findAllByOrderByNameAscIdAsc(Pageable page);
}
