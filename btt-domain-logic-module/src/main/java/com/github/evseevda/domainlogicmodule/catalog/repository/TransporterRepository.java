package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Читает данные каталога для TransporterEntity. */
public interface TransporterRepository extends JpaRepository<TransporterEntity, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query(
            """
                select c from TransporterEntity c where (:organization is null or c.organization.id = :organization)
                    and (:kind is null or c.transportKind.id = :kind)
                order by c.createdAt desc, c.id
                """)
    List<TransporterEntity> findAllMatchingOrganizationAndTransportKind(
            UUID organization, UUID kind, Pageable page);

    /** Блокирует перевозчика на время проверки права публиковать рейсы. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from TransporterEntity t where t.id = :id")
    Optional<TransporterEntity> findByIdForSchedulingWithLock(UUID id);
}
