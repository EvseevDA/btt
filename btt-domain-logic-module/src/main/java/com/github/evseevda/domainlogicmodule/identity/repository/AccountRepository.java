package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Сохраняет аккаунты и читает роли и профили. */
public interface AccountRepository extends JpaRepository<UserAccount, UUID>, AccountRepositoryCustom {
    /** Ищет аккаунт по логину. */
    Optional<UserAccount> findByUsername(String username);

    /** Выбирает исполнителей, которым назначено указанное разрешение. */
    @Query("select distinct u from UserAccount u join u.roles r join r.permissions p where p = :permission order by u.fullName, u.id")
    List<UserAccount> findStaffWithPermission(String permission);
}
