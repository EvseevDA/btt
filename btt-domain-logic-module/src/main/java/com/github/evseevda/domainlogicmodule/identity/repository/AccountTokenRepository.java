package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.domain.AccountToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранит хеши токенов и читает данные для аутентификации. */
public interface AccountTokenRepository extends JpaRepository<AccountToken, String>, AccountTokenRepositoryCustom {
    /** Идентификатор и хеш пароля для проверки учётных данных. */
    record Credential(UUID id, String hash) {
    }
}
