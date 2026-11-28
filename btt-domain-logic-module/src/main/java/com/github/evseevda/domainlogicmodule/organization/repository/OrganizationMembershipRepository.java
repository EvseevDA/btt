package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembershipEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Читает списки организаций аккаунта и участников организации. */
public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembershipEntity, OrganizationMembershipEntity.Key> {
    /** Возвращает членства текущего пользователя. */
    List<OrganizationMembershipEntity> findAllByIdUserId(UUID userId);

    /** Возвращает зарегистрированных участников организации. */
    List<OrganizationMembershipEntity> findAllByIdOrganizationId(UUID organizationId);
}
