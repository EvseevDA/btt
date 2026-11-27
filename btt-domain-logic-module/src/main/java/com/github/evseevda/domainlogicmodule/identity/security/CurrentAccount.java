package com.github.evseevda.domainlogicmodule.identity.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class CurrentAccount {
    private CurrentAccount() {
    }

    public static UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    public static boolean hasPermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    public static void require(String permission) {
        if (!hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
