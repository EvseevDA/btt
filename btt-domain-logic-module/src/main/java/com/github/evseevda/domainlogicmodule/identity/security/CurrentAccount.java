package com.github.evseevda.domainlogicmodule.identity.security;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Предоставляет идентификатор и разрешения пользователя текущего запроса. */
public final class CurrentAccount {
    private CurrentAccount() {}

    /**
     * Возвращает идентификатор аутентифицированного пользователя. Для анонимного запроса
     * возвращается ошибка 401.
     */
    public static UUID getUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    /** Проверяет наличие указанного разрешения у текущего пользователя. */
    public static boolean hasPermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    /** Возвращает действующие разрешения для интерфейса кабинета. */
    public static List<String> getPermissions() {
        getUserId();
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
    }

    /** Требует указанное разрешение. При его отсутствии возвращается ошибка 403. */
    public static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
