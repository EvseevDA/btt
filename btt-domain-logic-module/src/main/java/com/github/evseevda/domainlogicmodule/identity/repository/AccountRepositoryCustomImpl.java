package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;
import com.github.evseevda.domainlogicmodule.identity.domain.Role;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Сохраняет аккаунты и читает роли и профили. */
class AccountRepositoryCustomImpl extends JpaQueries implements AccountRepositoryCustom {
    /** Подключает репозиторий аккаунтов и EntityManager. */
    AccountRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Блокирует роль администратора во время создания первого аккаунта. */
    @Override
    public UUID lockPortalAdmin() {
        return select("select r from Role r where r.name = 'PORTAL_ADMIN'", Role.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult().getId();
    }

    /** Проверяет, назначена ли кому-либо роль администратора. */
    @Override
    public boolean hasPortalAdmin() {
        return select("select u.id from UserAccount u join u.roles r where r.name = 'PORTAL_ADMIN'", UUID.class)
                .setMaxResults(1).getResultList().size() == 1;
    }

    /** Читает страницу ролей аккаунта. */
    @Override
    public List<RoleDto> roles(UUID id, int limit, int offset) {
        return select("select r from UserAccount u join u.roles r where u.id = :id order by r.name", Role.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(role -> new RoleDto(role.getId(), role.getName()).description(role.getDescription())).toList();
    }

    /** Сохраняет новый аккаунт и хеш пароля. */
    @Override
    public void insertAccount(UUID id, String username, String email, String name, String hash, LocalDateTime created) {
        insert(new UserAccount(id, username, email, name, hash, created));
    }

    /** Назначает роль по имени, если такая роль существует. */
    @Override
    public int assignRole(UUID id, String role) {
        var found = one(select("select r from Role r where r.name = :name", Role.class).setParameter("name", role));
        found.ifPresent(value -> ref(UserAccount.class, id).assignRole(value));
        return found.isPresent() ? 1 : 0;
    }

    /** Читает профиль аккаунта без хеша пароля. */
    @Override
    public Optional<UserAccountDto> findProfile(UUID id) {
        return Optional.ofNullable(em.find(UserAccount.class, id)).map(account -> new UserAccountDto(id, account.getUsername(), account.getEmail(),
                account.getFullName(), account.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }
}
