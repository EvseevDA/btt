package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Читает данные каталога для Transporter. */
public interface TransporterRepository extends JpaRepository<Transporter, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query("""
                select c from Transporter c where (:organization is null or c.organization.id = :organization)
                    and (:kind is null or c.transportKind.id = :kind)
                order by c.createdAt desc, c.id
                """)
    List<Transporter> findTransporters(UUID organization, UUID kind, Pageable page);

    /** Блокирует перевозчика на время проверки права публиковать рейсы. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Transporter t where t.id = :id")
    Optional<Transporter> findForScheduling(UUID id);
}
