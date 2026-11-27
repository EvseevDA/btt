package com.github.evseevda.domainlogicmodule.identity.service;

import com.github.evseevda.domainlogic.model.RegisterUser;
import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.identity.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Создаёт аккаунты, назначает роли и проверяет доступ к профилям. */
@Service
@Profile("!mock-api")
public class AccountService {
    private final AccountRepository repository;
    private final PasswordEncoder passwords;

    /** Создаёт сервис аккаунтов с подключением к базе и кодировщиком паролей. */
    public AccountService(AccountRepository repository, PasswordEncoder passwords) {
        this.repository = repository;
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
        repository.lockPortalAdmin();
        boolean exists = repository.hasPortalAdmin();
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
        return repository.roles(id, limit, offset);
    }

    private UUID create(String username, String email, String fullName, String password) {
        if (username.isBlank() || email.isBlank() || fullName.isBlank() || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректные данные аккаунта");
        }
        UUID id = UUID.randomUUID();
        try {
            repository.insertAccount(id, username.strip().toLowerCase(Locale.ROOT), email.strip().toLowerCase(Locale.ROOT), fullName.strip(), passwords.encode(password), LocalDateTime.now(Clock.systemUTC()));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин или почта уже используется", ex);
        }
        return id;
    }

    private void assignRole(UUID id, String role) {
        int inserted = repository.assignRole(id, role);
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль");
        }
    }

    private UserAccountDto find(UUID id) {
        return repository.findProfile(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void checkAccess(UUID id) {
        if (!CurrentAccount.id().equals(id)) {
            CurrentAccount.require("account.staff.create");
        }
    }
}
