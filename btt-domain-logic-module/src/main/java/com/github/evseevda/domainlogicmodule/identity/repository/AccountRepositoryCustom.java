package com.github.evseevda.domainlogicmodule.identity.repository;

import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface AccountRepositoryCustom {
    /** Блокирует роль администратора во время создания первого аккаунта. */
    UUID lockPortalAdmin();

    /** Проверяет, назначена ли кому-либо роль администратора. */
    boolean hasPortalAdmin();

    /** Читает страницу ролей аккаунта. */
    List<RoleDto> roles(UUID id, int limit, int offset);

    /** Сохраняет новый аккаунт и хеш пароля. */
    void insertAccount(UUID id, String username, String email, String name, String hash, LocalDateTime created);

    /** Назначает роль по имени, если такая роль существует. */
    int assignRole(UUID id, String role);

    /** Читает профиль аккаунта без хеша пароля. */
    Optional<UserAccountDto> findProfile(UUID id);
}
