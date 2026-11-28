package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccountEntity;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembershipEntity;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionEntity;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.TransporterRegistrationProjection;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.RegistrationRequestProjection;
import com.github.evseevda.domainlogicmodule.registration.request.domain.ProcessedRegistrationCommandEntity;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestEntity;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganizationEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/** Управляет заявлением, его решением и результатами команд Zeebe в транзакции процесса. */
class RegistrationRequestRepositoryCustomImpl extends JpaQueries
        implements RegistrationRequestRepositoryCustom {
    /** Подключает репозиторий заявлений и EntityManager для связанных записей. */
    RegistrationRequestRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Ищет организацию и текущее состояние перевозчика. */
    @Override
    public Optional<TransporterRegistrationProjection> findTransporterById(UUID transporter) {
        return Optional.ofNullable(em.find(TransporterEntity.class, transporter))
                .map(
                        carrier ->
                                new TransporterRegistrationProjection(
                                        carrier.getOrganization().getId(),
                                        carrier.getStatus().getId()));
    }

    /** Сохраняет черновик с исходной версией перевозчика и предложенным снимком. */
    @Override
    public void insertRequest(
            UUID id,
            UUID transporter,
            UUID organization,
            UUID source,
            String data,
            UUID actor,
            int type) {
        em.persist(
                new TransporterRequestEntity(
                        id,
                        getReference(TransporterEntity.class, transporter),
                        getReference(TransporterOrganizationEntity.class, organization),
                        getReference(TransporterVersionEntity.class, source),
                        data,
                        getReference(UserAccountEntity.class, actor),
                        TransporterRequestType.fromId(type),
                        getCurrentUtcDateTime()));
    }

    /** Блокирует перевозчика на время проверки другой открытой заявки. */
    @Override
    public void lockTransporterById(UUID transporter) {
        em.find(TransporterEntity.class, transporter, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Проверяет, рассматривается ли уже заявление по перевозчику. */
    @Override
    public boolean existsOpenRequestByTransporterId(UUID transporter) {
        return !createTypedQuery(
                        """
                select r.id from TransporterRequestEntity r where r.transporter.id = :id
                    and r.status in (SUBMITTED, UNDER_REVIEW, AWAITING_APPROVAL, REVISION_REQUIRED)
                """,
                        UUID.class)
                .setParameter("id", transporter)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /** Фиксирует подачу заявления. */
    @Override
    public void markSubmitted(UUID id) {
        getReference(TransporterRequestEntity.class, id).submit(getCurrentUtcDateTime());
    }

    /** Создаёт решение или сохраняет новую редакцию существующего проекта. */
    @Override
    public void saveDraftDecision(
            UUID decision, UUID id, UUID actor, int type, String comment, String description) {
        var author = getReference(UserAccountEntity.class, actor);
        var verdict = DecisionType.fromId(type);
        var existing = decision(id);
        if (existing.isPresent()) {
            existing.orElseThrow().revise(author, verdict, comment, description);
        } else {
            em.persist(
                    new DecisionEntity(
                            decision,
                            getReference(TransporterRequestEntity.class, id),
                            author,
                            verdict,
                            comment,
                            description));
        }
    }

    /** Читает результат уже обработанной команды, включая результат без решения. */
    @Override
    public Optional<Integer> findProcessedDecisionTypeByCommandIdAndRequestId(
            UUID command, UUID id) {
        return Optional.ofNullable(em.find(ProcessedRegistrationCommandEntity.class, command))
                .filter(processed -> id.equals(processed.getRequestId()))
                .map(
                        processed ->
                                processed.getDecisionType() == null
                                        ? 0
                                        : processed.getDecisionType());
    }

    /** Назначает исполнителя и переводит заявку на рассмотрение. */
    @Override
    public void assignExecutor(UUID executor, UUID id) {
        getReference(TransporterRequestEntity.class, id)
                .assign(getReference(UserAccountEntity.class, executor));
    }

    /** Сохраняет комментарий к возврату решения. */
    @Override
    public void saveReturnReason(String reason, UUID id) {
        decision(id).orElseThrow().recordReturn(reason);
    }

    /** Фиксирует результат команды для защиты от повторной обработки. */
    @Override
    public void insertProcessedCommand(UUID command, UUID id, Integer result) {
        em.persist(new ProcessedRegistrationCommandEntity(command, id, result));
    }

    /** Убирает ссылку на выполненную команду. */
    @Override
    public void clearPendingAction(UUID id) {
        getReference(TransporterRequestEntity.class, id).recordPending(null, null);
    }

    /** Читает описание изменений из решения. */
    @Override
    public Optional<String> findChangeDescriptionByRequestId(UUID id) {
        return decision(id).map(DecisionEntity::getChangeDescription);
    }

    /** Связывает заявление с зарегистрированным перевозчиком. */
    @Override
    public void attachTransporter(UUID transporter, UUID id) {
        getReference(TransporterRequestEntity.class, id)
                .attach(getReference(TransporterEntity.class, transporter));
    }

    /** Сохраняет согласовавшего и время итогового решения. */
    @Override
    public void markDecisionApproved(UUID reviewer, UUID id) {
        decision(id)
                .orElseThrow()
                .approve(getReference(UserAccountEntity.class, reviewer), getCurrentUtcDateTime());
    }

    /** Связывает ожидающую команду с заявлением. */
    @Override
    public void savePendingAction(UUID id, String action, UUID command) {
        getReference(TransporterRequestEntity.class, id).recordPending(action, command);
    }

    /** Сохраняет новое состояние заявления. */
    @Override
    public void updateStatus(int status, UUID id) {
        getReference(TransporterRequestEntity.class, id)
                .changeStatus(TransporterRequestStatus.fromId(status));
    }

    /** Читает текущую редакцию решения. */
    @Override
    public Optional<DecisionDto> findDecisionByRequestId(UUID id) {
        return decision(id)
                .map(
                        d ->
                                new DecisionDto(
                                                d.getId(),
                                                id,
                                                d.getDecidedBy().getId(),
                                                DecisionDto.TypeEnum.fromValue(d.getType().getId()),
                                                d.getStatus().getId())
                                        .revision(d.getRevision())
                                        .approvedById(
                                                d.getApprovedBy() == null
                                                        ? null
                                                        : d.getApprovedBy().getId())
                                        .returnReason(d.getReturnReason())
                                        .comment(d.getComment())
                                        .decidedAt(date(d.getDecidedAt())));
    }

    /** Читает заявление с блокировкой на запись для переходов процесса. */
    @Override
    public Optional<RegistrationRequestProjection> findRegistrationRequestById(
            UUID id, boolean lock) {
        var found =
                Optional.ofNullable(
                        em.find(
                                TransporterRequestEntity.class,
                                id,
                                lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE));
        return found.map(RegistrationRequestRepositoryCustomImpl::projection);
    }

    /** Проверяет право пользователя представлять организацию. */
    @Override
    public boolean mayRepresent(UUID org, UUID user) {
        var member =
                em.find(
                        OrganizationMembershipEntity.class,
                        new OrganizationMembershipEntity.Key(org, user));
        return member != null && member.isCanRepresent();
    }

    /** Проверяет разрешение исполнителя по его ролям. */
    @Override
    public boolean hasPermission(UUID user, String permission) {
        return !createTypedQuery(
                        """
                select u.id from UserAccountEntity u join u.roles r join r.permissions p where u.id = :id and p = :permission
                """,
                        UUID.class)
                .setParameter("id", user)
                .setParameter("permission", permission)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    private Optional<DecisionEntity> decision(UUID id) {
        return findSingleResult(
                createTypedQuery(
                                "select d from DecisionEntity d where d.request.id = :id",
                                DecisionEntity.class)
                        .setParameter("id", id));
    }

    static RegistrationRequestProjection projection(TransporterRequestEntity request) {
        var dto =
                new TransporterRequestDto(
                                request.getOrganization().getId(),
                                request.getId(),
                                request.getApplicantContact().getId(),
                                TransporterRequestDto.TypeEnum.fromValue(request.getType().getId()),
                                request.getStatus().getId(),
                                date(request.getCreatedAt()))
                        .transporterId(
                                request.getTransporter() == null
                                        ? null
                                        : request.getTransporter().getId())
                        .executorId(
                                request.getExecutor() == null
                                        ? null
                                        : request.getExecutor().getId())
                        .sourceTransporterVersionId(
                                request.getSourceTransporterVersion() == null
                                        ? null
                                        : request.getSourceTransporterVersion().getId())
                        .submittedAt(date(request.getSubmittedAt()))
                        .workflowActionPending(request.getPendingAction() != null);
        return new RegistrationRequestProjection(
                dto,
                request.getPendingAction(),
                request.getPendingCommandId(),
                request.getProposedData());
    }

    private static String date(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
