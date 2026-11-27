package com.github.evseevda.domainlogicmodule.transporter.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Юридическое лицо, к которому относятся пользователи и перевозчики. */
@Entity
@Getter
@Table(name = "transporter_organization")
public class TransporterOrganization {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Отображаемое название. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** ИНН организации. */
    @Column(name = "inn", nullable = false, unique = true, length = 12)
    private String inn;

    /** ОГРН организации. */
    @Column(name = "ogrn", nullable = false, unique = true, length = 15)
    private String ogrn;

    /** Юридический адрес организации. */
    @Column(name = "legal_address", nullable = false, columnDefinition = "text")
    private String legalAddress;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Участники организации. */
    @ManyToMany
    @JoinTable(
            name = "transporter_organization_users",
            joinColumns = @JoinColumn(name = "organization_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserAccount> users = new HashSet<>();

    protected TransporterOrganization() {
    }
}
