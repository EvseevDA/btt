package com.github.evseevda.domainlogicmodule.identity.service;

import com.github.evseevda.domainlogic.model.RegisterUser;
import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;
import com.github.evseevda.domainlogicmodule.identity.repository.AccountRepository;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public UserAccountDto registerUser(RegisterUser input) {
        var roles = input.getRoleNames();
        if (roles != null && !roles.isEmpty()) {
            CurrentAccount.getUserId();
            CurrentAccount.requirePermission("account.staff.create");
        }
        UUID id =
                createAccount(
                        input.getUsername(),
                        input.getEmail(),
                        input.getFullName(),
                        input.getPassword());
        if (roles != null) {
            roles.stream().distinct().forEach(role -> assignRole(id, role));
        }
        return getAccountById(id);
    }

    /** Создаёт первого администратора, если эта роль ещё никому не назначена. */
    @Transactional
    public void createInitialAdministrator(String username, String email, String password) {
        repository.findPortalAdminRoleIdWithLock();
        boolean exists = repository.existsPortalAdministrator();
        if (!exists) {
            assignRole(
                    createAccount(username, email, "Администратор портала", password),
                    "PORTAL_ADMIN");
        }
    }

    /** Возвращает собственный профиль или профиль, доступный администратору. */
    public UserAccountDto getUserById(UUID userId) {
        requireProfileAccess(userId);
        return getAccountById(userId);
    }

    /** Читает роли аккаунта с проверкой доступа и разбивкой на страницы. */
    public List<RoleDto> findAllRolesByUserId(UUID userId, int offset, int limit) {
        requireProfileAccess(userId);
        getAccountById(userId);
        return repository.findAllRolesByUserId(userId, limit, offset);
    }

    private UUID createAccount(String username, String email, String fullName, String password) {
        if (username.isBlank()
                || email.isBlank()
                || fullName.isBlank()
                || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Некорректные данные аккаунта");
        }
        UUID id = UUID.randomUUID();
        try {
            repository.insertAccount(
                    id,
                    username.strip().toLowerCase(Locale.ROOT),
                    email.strip().toLowerCase(Locale.ROOT),
                    fullName.strip(),
                    passwords.encode(password),
                    LocalDateTime.now(Clock.systemUTC()));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Логин или почта уже используется", ex);
        }
        return id;
    }

    private void assignRole(UUID id, String role) {
        int inserted = repository.assignRole(id, role);
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная роль");
        }
    }

    private UserAccountDto getAccountById(UUID id) {
        return repository
                .findProfileById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireProfileAccess(UUID id) {
        if (!CurrentAccount.getUserId().equals(id)) {
            CurrentAccount.requirePermission("account.staff.create");
        }
    }
}
