package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.repository.AccountTokenRepository.Credential;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface AccountTokenRepositoryCustom {
    /** Ищет учётные данные по имени пользователя. */
    Optional<Credential> findCredential(String username);

    /** Записывает хеш токена и срок его действия. */
    void insertToken(String hash, UUID user, LocalDateTime expiry);

    /** Ищет владельца непросроченного токена. */
    Optional<UUID> findTokenOwner(String hash, LocalDateTime now);

    /** Возвращает разрешения из ролей аккаунта. */
    List<String> permissions(UUID id);
}
