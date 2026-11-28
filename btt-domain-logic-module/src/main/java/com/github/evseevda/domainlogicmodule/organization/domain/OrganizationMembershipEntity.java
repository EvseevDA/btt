package com.github.evseevda.domainlogicmodule.organization.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;

/** Связь пользователя с организацией и предоставленные ему полномочия. */
@Entity
@Getter
@Table(name = "transporter_organization_users")
public class OrganizationMembershipEntity {
    @EmbeddedId private Key id;
    private boolean canRepresent;
    private boolean canConfirmMembers;

    protected OrganizationMembershipEntity() {}

    /** Создаёт членство с указанными полномочиями. */
    public OrganizationMembershipEntity(Key id, boolean canRepresent, boolean canConfirmMembers) {
        this.id = id;
        changePermissions(canRepresent, canConfirmMembers);
    }

    /** Устанавливает полномочия участника организации. */
    public void changePermissions(boolean represent, boolean confirm) {
        canRepresent = represent;
        canConfirmMembers = confirm;
    }

    /** Ключ членства определён парой организации и пользователя. */
    @Embeddable
    public record Key(UUID organizationId, UUID userId) implements Serializable {}
}
