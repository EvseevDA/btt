package com.github.evseevda.domainlogicmodule.identity.resource;

import com.github.evseevda.domainlogic.api.SessionApi;
import com.github.evseevda.domainlogic.model.AccountSummary;
import com.github.evseevda.domainlogic.model.OrganizationMemberDto;
import com.github.evseevda.domainlogic.model.SessionDto;
import com.github.evseevda.domainlogicmodule.identity.service.SessionService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Предоставляет фронтенду данные текущего аккаунта и списки сотрудников. */
@RestController
public class SessionController implements SessionApi {
    private final ObjectProvider<SessionService> sessions;

    /** Подключает сервис кабинета, если включён прикладной режим. */
    public SessionController(ObjectProvider<SessionService> sessions) {
        this.sessions = sessions;
    }

    /** Возвращает данные текущей сессии. */
    @Override
    public ResponseEntity<SessionDto> getSession() {
        var service = sessions.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.getCurrentSession());
    }

    /** Возвращает сотрудников выбранной организации в пределах полномочий вызывающего. */
    @Override
    public ResponseEntity<List<OrganizationMemberDto>> listOrganizationMembers(
            UUID organizationId) {
        var service = sessions.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.findAllMembersByOrganizationId(organizationId));
    }

    /** Возвращает доступных исполнителей процесса. */
    @Override
    public ResponseEntity<List<AccountSummary>> listStaff(String permission) {
        var service = sessions.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.findAllAssignableStaff(permission));
    }
}
