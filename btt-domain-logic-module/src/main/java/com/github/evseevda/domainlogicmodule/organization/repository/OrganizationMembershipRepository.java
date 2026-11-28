package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Читает списки организаций аккаунта и участников организации. */
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, OrganizationMembership.Key> {
    /** Возвращает членства текущего пользователя. */
    List<OrganizationMembership> findAllByIdUserId(UUID userId);

    /** Возвращает зарегистрированных участников организации. */
    List<OrganizationMembership> findAllByIdOrganizationId(UUID organizationId);
}
