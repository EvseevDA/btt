package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Читает данные каталога для TransporterLicenseEntity. */
public interface TransporterLicenseRepository
        extends JpaRepository<TransporterLicenseEntity, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query(
            """
                select l from TransporterLicenseEntity l join l.transporterVersion v
                where v.transporter.id = :id and v.versionNumber = (select max(version.versionNumber)
                    from TransporterVersionEntity version where version.transporter.id = :id)
                order by l.licenseNumber, l.id
                """)
    List<TransporterLicenseEntity> findAllCurrentByTransporterId(UUID id, Pageable page);
}
