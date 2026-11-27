package com.github.evseevda.domainlogicmodule.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Хеш выданного токена и срок его действия. */
@Entity
@Getter
@Table(name = "account_token")
public class AccountToken {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String tokenHash;
    private UUID userId;
    private LocalDateTime expiresAt;

    protected AccountToken() {
    }

    /** Создаёт запись токена, не сохраняя его исходное значение. */
    public AccountToken(String tokenHash, UUID userId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
