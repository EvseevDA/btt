package com.github.evseevda.domainlogicmodule.catalog.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicense;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Читает данные каталога для TransporterLicense. */
public interface TransporterLicenseRepository extends JpaRepository<TransporterLicense, UUID> {
    /** Возвращает страницу записей в порядке каталога. */
    @Query("""
                select l from TransporterLicense l join l.transporterVersion v
                where v.transporter.id = :id and v.versionNumber = (select max(version.versionNumber)
                    from TransporterVersion version where version.transporter.id = :id)
                order by l.licenseNumber, l.id
                """)
    List<TransporterLicense> findCurrentLicenses(UUID id, Pageable page);
}
