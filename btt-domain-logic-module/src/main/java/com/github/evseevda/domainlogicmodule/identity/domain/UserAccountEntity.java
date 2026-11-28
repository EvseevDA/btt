package com.github.evseevda.domainlogicmodule.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/** Учётная запись пользователя с профилем и назначенными ролями. */
@Entity
@Getter
@Table(name = "\"user\"")
public class UserAccountEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Логин для входа. */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /** Адрес электронной почты. */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Отображаемое имя пользователя. */
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Роли аккаунта. */
    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    /** Хеш пароля. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    protected UserAccountEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public UserAccountEntity(
            UUID id,
            String username,
            String email,
            String fullName,
            String passwordHash,
            LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /** Назначает роль аккаунту. */
    public void assignRole(RoleEntity role) {
        roles.add(role);
    }
}
