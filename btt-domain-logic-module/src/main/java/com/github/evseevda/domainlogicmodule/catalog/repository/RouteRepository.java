package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.route.domain.Route;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Читает данные каталога для Route. */
public interface RouteRepository extends JpaRepository<Route, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query("""
                select r from Route r where :kind is null or r.transportKind.id = :kind
                order by r.source, r.dest, r.id
                """)
    List<Route> findRoutes(UUID kind, Pageable page);
}
