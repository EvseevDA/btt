package com.github.evseevda.domainlogicmodule.identity.domain;

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

/** Учётная запись пользователя с профилем и назначенными ролями. */
@Entity
@Getter
@Table(name = "\"user\"")
public class UserAccount {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /** Хеш пароля. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    protected UserAccount() {
    }
}
