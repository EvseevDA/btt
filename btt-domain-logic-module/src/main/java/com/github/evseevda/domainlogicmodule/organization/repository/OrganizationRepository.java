package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganization;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Читает справочник подключённых организаций. */
public interface OrganizationRepository extends JpaRepository<TransporterOrganization, UUID> {
    /** Проверяет уникальность реквизитов организации. */
    boolean existsByInnOrOgrn(String inn, String ogrn);

    /** Ищет организации по части названия или точным реквизитам. */
    @Query("""
            select o from TransporterOrganization o where :search = '' or
                locate(lower(:search), lower(o.name)) > 0 or o.inn = :search or o.ogrn = :search
            order by o.name, o.id
            """)
    List<TransporterOrganization> searchOrganizations(String search, Pageable page);
}
