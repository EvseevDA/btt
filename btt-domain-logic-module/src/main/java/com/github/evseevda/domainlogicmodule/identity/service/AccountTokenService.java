package com.github.evseevda.domainlogicmodule.identity.service;

import com.github.evseevda.domainlogicmodule.identity.repository.AccountTokenRepository.AccountCredentialProjection;
import com.github.evseevda.domainlogicmodule.identity.repository.AccountTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Выдаёт непрозрачные токены доступа и восстанавливает разрешения аккаунта при аутентификации. */
@Service
@Profile("!mock-api")
public class AccountTokenService {
    private final AccountTokenRepository repository;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    private final String unknownAccountHash;

    /** Создаёт сервис токенов с подключением к базе и кодировщиком паролей. */
    public AccountTokenService(AccountTokenRepository repository, PasswordEncoder passwords) {
        this.repository = repository;
        this.passwords = passwords;
        this.unknownAccountHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** Проверяет пароль и выдаёт токен, действующий восемь часов. */
    public String issueToken(String username, String password) {
        var account =
                repository
                        .findCredentialByUsername(username.strip().toLowerCase(Locale.ROOT))
                        .filter(row -> row.hash() != null);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(
                        password,
                        account.map(AccountCredentialProjection::hash).orElse(unknownAccountHash))
                || account.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.insertToken(
                hashToken(token),
                account.orElseThrow().id(),
                LocalDateTime.now(Clock.systemUTC()).plusHours(8));
        return token;
    }

    /** Находит аккаунт по действующему токену и загружает его разрешения. */
    public Optional<Authentication> authenticateToken(String token) {
        if (token.length() != 43) {
            return Optional.empty();
        }
        return repository
                .findUserIdByTokenHashAndExpiration(
                        hashToken(token), LocalDateTime.now(Clock.systemUTC()))
                .map(
                        id ->
                                UsernamePasswordAuthenticationToken.authenticated(
                                        id,
                                        null,
                                        repository.findAllPermissionsByUserId(id).stream()
                                                .map(SimpleGrantedAuthority::new)
                                                .toList()));
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
