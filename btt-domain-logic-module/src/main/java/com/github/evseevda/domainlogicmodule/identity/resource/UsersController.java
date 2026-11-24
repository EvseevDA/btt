package com.github.evseevda.domainlogicmodule.identity.resource;

import com.github.evseevda.domainlogic.api.UsersApi;
import com.github.evseevda.domainlogic.model.RegisterUser;
import com.github.evseevda.domainlogic.model.RoleDto;
import com.github.evseevda.domainlogic.model.UserAccountDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class UsersController implements UsersApi {

    @Override
    public ResponseEntity<UserAccountDto> getUser(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<RoleDto>> listUserRoles(UUID userId, Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<UserAccountDto> registerUser(RegisterUser registerUser) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
