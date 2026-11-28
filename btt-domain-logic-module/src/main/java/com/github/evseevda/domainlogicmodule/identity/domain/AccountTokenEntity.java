package com.github.evseevda.domainlogicmodule.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Хеш выданного токена и срок его действия. */
@Entity
@Getter
@Table(name = "account_token")
public class AccountTokenEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String tokenHash;

    private UUID userId;
    private LocalDateTime expiresAt;

    protected AccountTokenEntity() {}

    /** Создаёт запись токена, не сохраняя его исходное значение. */
    public AccountTokenEntity(String tokenHash, UUID userId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
