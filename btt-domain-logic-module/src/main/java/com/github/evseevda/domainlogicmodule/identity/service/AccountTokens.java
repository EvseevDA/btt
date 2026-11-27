package com.github.evseevda.domainlogicmodule.identity.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

@Service
@Profile("!mock-api")
public class AccountTokens {
    private final JdbcClient db;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    private final String unknownAccountHash;

    public AccountTokens(JdbcClient db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
        this.unknownAccountHash = passwords.encode(UUID.randomUUID().toString());
    }

    public String issue(String username, String password) {
        var account = db.sql("SELECT id, password_hash FROM \"user\" WHERE username = :username")
                .param("username", username.strip().toLowerCase(Locale.ROOT))
                .query((rs, row) -> new Credential(rs.getObject("id", UUID.class), rs.getString("password_hash")))
                .optional().filter(row -> row.hash() != null);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, account.map(Credential::hash).orElse(unknownAccountHash))
                || account.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.sql("INSERT INTO account_token (token_hash, user_id, expires_at) VALUES (:hash, :user, :expiry)")
                .param("hash", hash(token)).param("user", account.orElseThrow().id())
                .param("expiry", LocalDateTime.now(Clock.systemUTC()).plusHours(8)).update();
        return token;
    }

    public Optional<Authentication> authenticate(String token) {
        if (token.length() != 43) {
            return Optional.empty();
        }
        return db.sql("SELECT user_id FROM account_token WHERE token_hash = :hash AND expires_at > :now")
                .param("hash", hash(token)).param("now", LocalDateTime.now(Clock.systemUTC()))
                .query(UUID.class).optional().map(id -> UsernamePasswordAuthenticationToken.authenticated(id, null,
                        db.sql("""
                                SELECT DISTINCT rp.permission FROM role_permission rp
                                JOIN user_roles ur ON ur.role_id = rp.role_id WHERE ur.user_id = :id
                                """).param("id", id).query(String.class).list().stream()
                                .map(SimpleGrantedAuthority::new).toList()));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Credential(UUID id, String hash) {
    }
}
