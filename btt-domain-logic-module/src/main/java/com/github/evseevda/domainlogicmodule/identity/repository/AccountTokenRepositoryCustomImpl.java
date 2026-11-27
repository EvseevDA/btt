package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.repository.AccountTokenRepository.Credential;
import com.github.evseevda.domainlogicmodule.identity.domain.AccountToken;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public Optional<Credential> findCredential(String username) {
        return accounts.findByUsername(username).map(account -> new Credential(account.getId(), account.getPasswordHash()));
    }

    /** Записывает хеш токена и срок его действия. */
    @Transactional
    @Override
    public void insertToken(String hash, UUID user, LocalDateTime expiry) {
        insert(new AccountToken(hash, user, expiry));
    }

    /** Ищет владельца непросроченного токена. */
    @Override
    public Optional<UUID> findTokenOwner(String hash, LocalDateTime now) {
        return one(select("select t.userId from AccountToken t where t.tokenHash = :hash and t.expiresAt > :now", UUID.class)
                .setParameter("hash", hash).setParameter("now", now));
    }

    /** Возвращает разрешения из ролей аккаунта. */
    @Override
    public List<String> permissions(UUID id) {
        return select("select distinct p from UserAccount u join u.roles r join r.permissions p where u.id = :id", String.class)
                .setParameter("id", id).getResultList();
    }

}
