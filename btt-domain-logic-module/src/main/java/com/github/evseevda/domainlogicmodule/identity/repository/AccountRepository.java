package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Сохраняет аккаунты и читает роли и профили. */
public interface AccountRepository extends JpaRepository<UserAccount, UUID>, AccountRepositoryCustom {
    /** Ищет аккаунт по логину. */
    Optional<UserAccount> findByUsername(String username);
}
