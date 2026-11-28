package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.domain.AccountTokenEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранит хеши токенов и читает данные для аутентификации. */
public interface AccountTokenRepository
        extends JpaRepository<AccountTokenEntity, String>, AccountTokenRepositoryCustom {
    /** Идентификатор и хеш пароля для проверки учётных данных. */
    record AccountCredentialProjection(UUID id, String hash) {}
}
