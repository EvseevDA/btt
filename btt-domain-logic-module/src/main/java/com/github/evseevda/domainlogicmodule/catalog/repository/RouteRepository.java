package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.route.domain.RouteEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Читает данные каталога для RouteEntity. */
public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query(
            """
                select r from RouteEntity r where :kind is null or r.transportKind.id = :kind
                order by r.source, r.dest, r.id
                """)
    List<RouteEntity> findAllMatchingTransportKind(UUID kind, Pageable page);
}
