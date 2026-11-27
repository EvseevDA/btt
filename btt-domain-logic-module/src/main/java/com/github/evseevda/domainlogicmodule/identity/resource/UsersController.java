package com.github.evseevda.domainlogicmodule.identity.resource;

import com.github.evseevda.domainlogic.api.UsersApi;
import com.github.evseevda.domainlogic.model.RegisterUser;
import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;

import com.github.evseevda.domainlogicmodule.identity.service.AccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Предоставляет REST-операции регистрации и чтения аккаунтов. */
@RestController
public class UsersController implements UsersApi {
    private final ObjectProvider<AccountService> accounts;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public UsersController(ObjectProvider<AccountService> accounts) {
        this.accounts = accounts;
    }

    /** Возвращает профиль пользователя после проверки доступа. */
    @Override
    public ResponseEntity<UserAccountDto> getUser(UUID id) {
        var service = accounts.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.profile(id));
    }

    /** Возвращает страницу ролей пользователя после проверки доступа. */
    @Override
    public ResponseEntity<List<RoleDto>> listUserRoles(UUID userId, Integer offset, Integer limit) {
        var service = accounts.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.roles(userId, offset, limit));
    }

    /** Создаёт аккаунт. Назначение ролей сотрудников требует отдельного разрешения. */
    @Override
    public ResponseEntity<UserAccountDto> registerUser(RegisterUser registerUser) {
        var service = accounts.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.register(registerUser));
    }
}
