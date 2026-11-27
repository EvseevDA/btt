package com.github.evseevda.domainlogicmodule.identity.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentAccountTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestsCannotReadAnAccountOrUsePermissions() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, CurrentAccount::id).getStatusCode());
        assertFalse(CurrentAccount.hasPermission("account.staff.create"));
    }

    @Test
    void permissionsAreMatchedExactly() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id, null,
                List.of(new SimpleGrantedAuthority("transporter.registration.decide"))));

        assertEquals(id, CurrentAccount.id());
        assertDoesNotThrow(() -> CurrentAccount.require("transporter.registration.decide"));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> CurrentAccount.require("transporter.amendment.decide")).getStatusCode());
    }
}
