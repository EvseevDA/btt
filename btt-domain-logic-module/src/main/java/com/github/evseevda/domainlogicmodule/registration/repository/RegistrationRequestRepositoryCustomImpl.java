package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Request;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Carrier;
import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembership;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.Decision;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;
import com.github.evseevda.domainlogicmodule.registration.request.domain.ProcessedRegistrationCommand;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequest;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganization;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/** Управляет заявлением, его решением и результатами команд Zeebe в транзакции процесса. */
class RegistrationRequestRepositoryCustomImpl extends JpaQueries implements RegistrationRequestRepositoryCustom {
    /** Подключает репозиторий заявлений и EntityManager для связанных записей. */
    RegistrationRequestRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Ищет организацию и текущее состояние перевозчика. */
    @Override
    public Optional<Carrier> findCarrier(UUID transporter) {
        return Optional.ofNullable(em.find(Transporter.class, transporter))
                .map(carrier -> new Carrier(carrier.getOrganization().getId(), carrier.getStatus().getId()));
    }

    /** Сохраняет черновик с исходной версией перевозчика и предложенным снимком. */
    @Override
    public void insertRequest(UUID id, UUID transporter, UUID organization, UUID source, String data, UUID actor, int type) {
        em.persist(new TransporterRequest(id, ref(Transporter.class, transporter),
                ref(TransporterOrganization.class, organization), ref(TransporterVersion.class, source), data,
                ref(UserAccount.class, actor), TransporterRequestType.fromId(type), now()));
    }

    /** Блокирует перевозчика на время проверки другой открытой заявки. */
    @Override
    public void lockCarrier(UUID transporter) {
        em.find(Transporter.class, transporter, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Проверяет, рассматривается ли уже заявление по перевозчику. */
    @Override
    public boolean hasOpenRequest(UUID transporter) {
        return !select("""
                select r.id from TransporterRequest r where r.transporter.id = :id
                    and r.status in (SUBMITTED, UNDER_REVIEW, AWAITING_APPROVAL, REVISION_REQUIRED)
                """, UUID.class).setParameter("id", transporter).setMaxResults(1).getResultList().isEmpty();
    }

    /** Фиксирует подачу заявления. */
    @Override
    public void markSubmitted(UUID id) {
        ref(TransporterRequest.class, id).submit(now());
    }

    /** Создаёт решение или сохраняет новую редакцию существующего проекта. */
    @Override
    public void saveDraftDecision(UUID decision, UUID id, UUID actor, int type, String comment, String description) {
        var author = ref(UserAccount.class, actor);
        var verdict = DecisionType.fromId(type);
        var existing = decision(id);
        if (existing.isPresent()) {
            existing.orElseThrow().revise(author, verdict, comment, description);
        } else {
            em.persist(new Decision(decision, ref(TransporterRequest.class, id), author, verdict, comment, description));
        }
    }

    /** Читает результат уже обработанной команды, включая результат без решения. */
    @Override
    public Optional<Integer> processedDecisionType(UUID command, UUID id) {
        return Optional.ofNullable(em.find(ProcessedRegistrationCommand.class, command))
                .filter(processed -> id.equals(processed.getRequestId()))
                .map(processed -> processed.getDecisionType() == null ? 0 : processed.getDecisionType());
    }

    /** Назначает исполнителя и переводит заявку на рассмотрение. */
    @Override
    public void assignExecutor(UUID executor, UUID id) {
        ref(TransporterRequest.class, id).assign(ref(UserAccount.class, executor));
    }

    /** Сохраняет комментарий к возврату решения. */
    @Override
    public void saveReturnReason(String reason, UUID id) {
        decision(id).orElseThrow().recordReturn(reason);
    }

    /** Фиксирует результат команды для защиты от повторной обработки. */
    @Override
    public void insertProcessedCommand(UUID command, UUID id, Integer result) {
        em.persist(new ProcessedRegistrationCommand(command, id, result));
    }

    /** Убирает ссылку на выполненную команду. */
    @Override
    public void clearPendingAction(UUID id) {
        ref(TransporterRequest.class, id).recordPending(null, null);
    }

    /** Читает описание изменений из решения. */
    @Override
    public Optional<String> findChangeDescription(UUID id) {
        return decision(id).map(Decision::getChangeDescription);
    }

    /** Связывает заявление с зарегистрированным перевозчиком. */
    @Override
    public void attachCarrier(UUID transporter, UUID id) {
        ref(TransporterRequest.class, id).attach(ref(Transporter.class, transporter));
    }

    /** Сохраняет согласовавшего и время итогового решения. */
    @Override
    public void markDecisionApproved(UUID reviewer, UUID id) {
        decision(id).orElseThrow().approve(ref(UserAccount.class, reviewer), now());
    }

    /** Связывает ожидающую команду с заявлением. */
    @Override
    public void savePendingAction(UUID id, String action, UUID command) {
        ref(TransporterRequest.class, id).recordPending(action, command);
    }

    /** Сохраняет новое состояние заявления. */
    @Override
    public void updateStatus(int status, UUID id) {
        ref(TransporterRequest.class, id).changeStatus(TransporterRequestStatus.fromId(status));
    }

    /** Читает текущую редакцию решения. */
    @Override
    public Optional<DecisionDto> findDecision(UUID id) {
        return decision(id).map(d -> new DecisionDto(d.getId(), id, d.getDecidedBy().getId(),
                DecisionDto.TypeEnum.fromValue(d.getType().getId()), d.getStatus().getId())
                .revision(d.getRevision()).approvedById(d.getApprovedBy() == null ? null : d.getApprovedBy().getId())
                .returnReason(d.getReturnReason()).comment(d.getComment()).decidedAt(date(d.getDecidedAt())));
    }

    /** Читает заявление с блокировкой на запись для переходов процесса. */
    @Override
    public Optional<Request> findRequest(UUID id, boolean lock) {
        var found = Optional.ofNullable(em.find(TransporterRequest.class, id,
                lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE));
        return found.map(RegistrationRequestRepositoryCustomImpl::projection);
    }

    /** Проверяет право пользователя представлять организацию. */
    @Override
    public boolean mayRepresent(UUID org, UUID user) {
        var member = em.find(OrganizationMembership.class, new OrganizationMembership.Key(org, user));
        return member != null && member.isCanRepresent();
    }

    /** Проверяет разрешение исполнителя по его ролям. */
    @Override
    public boolean hasPermission(UUID user, String permission) {
        return !select("""
                select u.id from UserAccount u join u.roles r join r.permissions p where u.id = :id and p = :permission
                """, UUID.class).setParameter("id", user).setParameter("permission", permission)
                .setMaxResults(1).getResultList().isEmpty();
    }

    private Optional<Decision> decision(UUID id) {
        return one(select("select d from Decision d where d.request.id = :id", Decision.class).setParameter("id", id));
    }

    static Request projection(TransporterRequest request) {
        var dto = new TransporterRequestDto(request.getOrganization().getId(), request.getId(),
                request.getApplicantContact().getId(), TransporterRequestDto.TypeEnum.fromValue(request.getType().getId()),
                request.getStatus().getId(), date(request.getCreatedAt()))
                .transporterId(request.getTransporter() == null ? null : request.getTransporter().getId())
                .executorId(request.getExecutor() == null ? null : request.getExecutor().getId())
                .sourceTransporterVersionId(request.getSourceTransporterVersion() == null ? null : request.getSourceTransporterVersion().getId())
                .submittedAt(date(request.getSubmittedAt()));
        return new Request(dto, request.getPendingAction(), request.getPendingCommandId(), request.getProposedData());
    }

    private static String date(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }


}
