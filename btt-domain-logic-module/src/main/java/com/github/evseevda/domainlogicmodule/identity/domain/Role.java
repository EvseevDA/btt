package com.github.evseevda.domainlogicmodule.identity.domain;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Роль портала, объединяющая набор разрешений. */
@Entity
@Getter
@Table(name = "role")
public class Role {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Отображаемое название. */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** Описание записи. */
    @Column(name = "description", length = 255)
    private String description;

    /** Разрешения, предоставляемые ролью. */
    @ElementCollection
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 100)
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }
}
