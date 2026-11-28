package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.domain.AccountTokenEntity;
import com.github.evseevda.domainlogicmodule.identity.repository.AccountTokenRepository.AccountCredentialProjection;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Хранит хеши токенов и читает данные для аутентификации. */
class AccountTokenRepositoryCustomImpl extends JpaQueries implements AccountTokenRepositoryCustom {
    private final AccountRepository accounts;

    /** Подключает репозиторий аккаунтов и EntityManager. */
    AccountTokenRepositoryCustomImpl(EntityManager em, AccountRepository accounts) {
        super(em);
        this.accounts = accounts;
    }

    /** Ищет учётные данные по имени пользователя. */
    @Override
    public Optional<AccountCredentialProjection> findCredentialByUsername(String username) {
        return accounts.findByUsername(username)
                .map(
                        account ->
                                new AccountCredentialProjection(
                                        account.getId(), account.getPasswordHash()));
    }

    /** Записывает хеш токена и срок его действия. */
    @Transactional
    @Override
    public void insertToken(String hash, UUID user, LocalDateTime expiry) {
        persistAndFlush(new AccountTokenEntity(hash, user, expiry));
    }

    /** Ищет владельца непросроченного токена. */
    @Override
    public Optional<UUID> findUserIdByTokenHashAndExpiration(String hash, LocalDateTime now) {
        return findSingleResult(
                createTypedQuery(
                                "select t.userId from AccountTokenEntity t where t.tokenHash = :hash and t.expiresAt > :now",
                                UUID.class)
                        .setParameter("hash", hash)
                        .setParameter("now", now));
    }

    /** Возвращает разрешения из ролей аккаунта. */
    @Override
    public List<String> findAllPermissionsByUserId(UUID id) {
        return createTypedQuery(
                        "select distinct p from UserAccountEntity u join u.roles r join r.permissions p where u.id = :id",
                        String.class)
                .setParameter("id", id)
                .getResultList();
    }
}
