package com.github.evseevda.domainlogicmodule.identity.resource;

import com.github.evseevda.domainlogic.api.AuthApi;
import com.github.evseevda.domainlogic.model.AccessToken;
import com.github.evseevda.domainlogic.model.Login;
import com.github.evseevda.domainlogicmodule.identity.service.AccountTokens;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock-api")
public class AuthController implements AuthApi {
    private final AccountTokens tokens;

    public AuthController(AccountTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public ResponseEntity<AccessToken> issueToken(Login login) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AccessToken(tokens.issue(login.getUsername(), login.getPassword()), 28_800));
    }
}
