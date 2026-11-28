package com.github.evseevda.domainlogicmodule.registration.resource;

import com.github.evseevda.domainlogic.api.RegistrationApi;
import com.github.evseevda.domainlogic.model.ReturnRegistrationDecision;
import com.github.evseevda.domainlogic.model.ReviewDecisionRevision;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogic.model.AssignRequestExecutor;
import com.github.evseevda.domainlogic.model.CreateTransporterRequest;
import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.IssueDecision;
import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;

import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationReadService;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationRequests;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Предоставляет REST-команды по регистрации перевозчика и чтение истории. */
@RestController
public class RegistrationController implements RegistrationApi {
    private final ObjectProvider<RegistrationRequests> requests;

    private final ObjectProvider<RegistrationReadService> read;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public RegistrationController(ObjectProvider<RegistrationRequests> requests,
                                  ObjectProvider<RegistrationReadService> read) {
        this.requests = requests;
        this.read = read;
    }

    /** Принимает команду назначения исполнителя по заявлению. */
    @Override
    public ResponseEntity<TransporterRequestDto> assignRegistrationRequestExecutor(
            UUID requestId,
            AssignRequestExecutor assignRequestExecutor
    ) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.assign(requestId, assignRequestExecutor.getExecutorId()));
    }

    /** Создаёт черновик заявления нужного типа. */
    @Override
    public ResponseEntity<TransporterRequestDto> createRegistrationRequest(
            CreateTransporterRequest createTransporterRequest
    ) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.create(createTransporterRequest));
    }

    /** Возвращает историческую версию указанного РУ. */
    @Override
    public ResponseEntity<RegistrationCertificateVersionDto> getCertificateVersion(UUID certificateId, UUID versionId) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.certificateVersion(certificateId, versionId));
    }

    /** Возвращает карточку РУ перевозчика и ссылку на его последнюю версию. */
    @Override
    public ResponseEntity<RegistrationCertificateDto> getRegistrationCertificate(UUID transporterId) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.certificate(transporterId));
    }

    /** Возвращает доступное пользователю решение по заявлению. */
    @Override
    public ResponseEntity<DecisionDto> getRegistrationDecision(UUID requestId) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.decision(requestId));
    }

    /** Возвращает доступную пользователю карточку заявления перевозчика. */
    @Override
    public ResponseEntity<TransporterRequestDto> getRegistrationRequest(UUID id) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.request(id));
    }

    /** Сохраняет решение исполнителя для последующего согласования. */
    @Override
    public ResponseEntity<DecisionDto> issueRegistrationDecision(UUID requestId, IssueDecision issueDecision) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.issue(requestId, issueDecision));
    }

    /** Возвращает страницу истории указанного РУ. */
    @Override
    public ResponseEntity<List<RegistrationCertificateVersionDto>> listCertificateVersions(
            UUID certificateId,
            Integer offset,
            Integer limit
    ) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.certificateVersions(certificateId, offset, limit));
    }

    /** Возвращает страницу доступных пользователю заявлений перевозчиков. */
    @Override
    public ResponseEntity<List<TransporterRequestDto>> listRegistrationRequests(
            UUID transporterId,
            Integer type,
            Integer status,
            Integer offset,
            Integer limit
    ) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.listRequests(transporterId, type, status, offset, limit));
    }

    /** Подаёт черновик заявления и ставит запуск процесса в очередь. */
    @Override
    public ResponseEntity<TransporterRequestDto> submitRegistrationRequest(UUID requestId) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.submit(requestId));
    }

    /** Принимает команду отзыва заявления от его подателя. */
    @Override
    public ResponseEntity<TransporterRequestDto> withdrawRegistrationRequest(UUID requestId) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.withdraw(requestId));
    }
    /** Принимает команду согласования выбранной редакции решения. */
    @Override
    public ResponseEntity<TransporterRequestDto> approveRegistrationDecision(UUID requestId, ReviewDecisionRevision review) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.review(requestId, review.getRevision(), null));
    }

    /** Принимает команду взятия заявления текущим пользователем в работу. */
    @Override
    public ResponseEntity<TransporterRequestDto> claimRegistrationRequest(UUID requestId) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.assign(requestId, CurrentAccount.id()));
    }

    /** Возвращает исторический снимок перевозчика с лицензиями. */
    @Override
    public ResponseEntity<TransporterVersionDto> getTransporterVersion(UUID transporterId, UUID versionId) {
        var service = read.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.transporterVersion(transporterId, versionId));
    }

    /** Принимает команду возврата редакции решения исполнителю с указанием причины. */
    @Override
    public ResponseEntity<TransporterRequestDto> returnRegistrationDecision(UUID requestId, ReturnRegistrationDecision review) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.review(requestId, review.getRevision(), review.getReason()));
    }
}
