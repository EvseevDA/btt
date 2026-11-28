package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранит заявки организаций и членство пользователей. */
public interface OrganizationRequestRepository
        extends JpaRepository<OrganizationRequestEntity, UUID>,
                OrganizationRequestRepositoryCustom {}
