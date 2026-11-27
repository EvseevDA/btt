package com.github.evseevda.domainlogicmodule.identity.service;

import com.github.evseevda.domainlogic.model.RegisterUser;
import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Создаёт аккаунты, назначает роли и проверяет доступ к профилям. */
@Service
@Profile("!mock-api")
public class AccountService {
    private final JdbcClient db;
    private final PasswordEncoder passwords;

    /** Создаёт сервис аккаунтов с подключением к базе и кодировщиком паролей. */
    public AccountService(JdbcClient db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }

    /** Создаёт публичный аккаунт или, при наличии разрешения, аккаунт с ролями сотрудника. */
    @Transactional
    public UserAccountDto register(RegisterUser input) {
        var roles = input.getRoleNames();
        if (roles != null && !roles.isEmpty()) {
            CurrentAccount.id();
            CurrentAccount.require("account.staff.create");
        }
        UUID id = create(input.getUsername(), input.getEmail(), input.getFullName(), input.getPassword());
        if (roles != null) {
            roles.stream().distinct().forEach(role -> assignRole(id, role));
        }
        return find(id);
    }

    /** Создаёт первого администратора, если эта роль ещё никому не назначена. */
    @Transactional
    public void bootstrap(String username, String email, String password) {
        db.sql("SELECT id FROM role WHERE name = 'PORTAL_ADMIN' FOR UPDATE").query(UUID.class).single();
        boolean exists = db.sql("""
                SELECT EXISTS (SELECT 1 FROM user_roles ur JOIN role r ON r.id = ur.role_id
                               WHERE r.name = 'PORTAL_ADMIN')
                """).query(Boolean.class).single();
        if (!exists) {
            assignRole(create(username, email, "Администратор портала", password), "PORTAL_ADMIN");
        }
    }

    /** Возвращает собственный профиль или профиль, доступный администратору. */
    public UserAccountDto profile(UUID id) {
        checkAccess(id);
        return find(id);
    }

    /** Читает роли аккаунта с проверкой доступа и разбивкой на страницы. */
    public List<RoleDto> roles(UUID id, int offset, int limit) {
        checkAccess(id);
        find(id);
        return db.sql("""
                SELECT r.id, r.name, r.description FROM user_roles ur JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = :id ORDER BY r.name LIMIT :limit OFFSET :offset
                """).param("id", id).param("limit", limit).param("offset", offset)
                .query((rs, row) -> new RoleDto(rs.getObject("id", UUID.class), rs.getString("name"))
                        .description(rs.getString("description"))).list();
    }

    private UUID create(String username, String email, String fullName, String password) {
        if (username.isBlank() || email.isBlank() || fullName.isBlank() || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректные данные аккаунта");
        }
        UUID id = UUID.randomUUID();
        try {
            db.sql("""
                    INSERT INTO "user" (id, username, email, full_name, password_hash, created_at)
                    VALUES (:id, :username, :email, :name, :password, :created)
                    """).param("id", id).param("username", username.strip().toLowerCase(Locale.ROOT))
                    .param("email", email.strip().toLowerCase(Locale.ROOT)).param("name", fullName.strip())
                    .param("password", passwords.encode(password)).param("created", LocalDateTime.now(Clock.systemUTC()))
                    .update();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин или почта уже используется", ex);
        }
        return id;
    }

    private void assignRole(UUID id, String role) {
        int inserted = db.sql("""
                INSERT INTO user_roles (user_id, role_id) SELECT :id, id FROM role WHERE name = :role
                """).param("id", id).param("role", role).update();
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль");
        }
    }

    private UserAccountDto find(UUID id) {
        return db.sql("SELECT id, username, email, full_name, created_at FROM \"user\" WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new UserAccountDto(id, rs.getString("username"), rs.getString("email"),
                        rs.getString("full_name"), DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                                rs.getObject("created_at", LocalDateTime.class)))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void checkAccess(UUID id) {
        if (!CurrentAccount.id().equals(id)) {
            CurrentAccount.require("account.staff.create");
        }
    }
}
