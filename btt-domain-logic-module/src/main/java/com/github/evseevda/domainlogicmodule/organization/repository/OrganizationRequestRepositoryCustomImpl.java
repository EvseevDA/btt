package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembershipEntity;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestEntity;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestType;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganizationEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит заявки организаций и членство пользователей. */
class OrganizationRequestRepositoryCustomImpl extends JpaQueries
        implements OrganizationRequestRepositoryCustom {
    private final OrganizationRepository organizations;

    /** Подключает репозиторий организаций и EntityManager. */
    OrganizationRequestRepositoryCustomImpl(
            EntityManager em, OrganizationRepository organizations) {
        super(em);
        this.organizations = organizations;
    }

    /** Проверяет наличие организации с такими реквизитами. */
    @Override
    public boolean existsByInnOrOgrn(String inn, String ogrn) {
        return organizations.existsByInnOrOgrn(inn, ogrn);
    }

    /** Сохраняет заявку на подключение. */
    @Override
    public void insertRegistration(
            UUID request,
            UUID applicant,
            String name,
            String inn,
            String ogrn,
            String address,
            String evidence) {
        persistAndFlush(
                new OrganizationRequestEntity(
                        request,
                        applicant,
                        null,
                        OrganizationRequestType.REGISTRATION,
                        name,
                        inn,
                        ogrn,
                        address,
                        evidence,
                        getCurrentUtcDateTime()));
    }

    /** Проверяет, что организация подключена. */
    @Override
    public boolean existsOrganizationById(UUID organization) {
        return organizations.existsById(organization);
    }

    /** Проверяет членство пользователя в организации. */
    @Override
    public boolean isMember(UUID organization, UUID applicant) {
        return member(organization, applicant) != null;
    }

    /** Сохраняет заявку на присоединение к организации. */
    @Override
    public void insertMembership(UUID request, UUID applicant, UUID organization) {
        persistAndFlush(
                new OrganizationRequestEntity(
                        request,
                        applicant,
                        organization,
                        OrganizationRequestType.MEMBERSHIP,
                        null,
                        null,
                        null,
                        null,
                        null,
                        getCurrentUtcDateTime()));
    }

    /** Меняет полномочия участника, если тот состоит в организации. */
    @Override
    public int updatePermissions(UUID organization, UUID user, boolean represent, boolean confirm) {
        var member = member(organization, user);
        if (member == null) {
            return 0;
        }
        member.changePermissions(represent, confirm);
        return 1;
    }

    /** Читает доступные пользователю заявки с фильтрами по виду и состоянию. */
    @Override
    public List<OrganizationRequestDto> findAllVisibleRequests(
            UUID actor, boolean mayReview, Integer type, Integer status, int offset, int limit) {
        var kind = type == null ? null : OrganizationRequestType.fromId(type);
        var state = status == null ? null : OrganizationRequestStatus.fromId(status);
        return createTypedQuery(
                        """
                select r from OrganizationRequestEntity r
                where (r.applicantId = :actor or (r.type = REGISTRATION and :mayReview = true)
                    or (r.type = MEMBERSHIP and exists (select m from OrganizationMembershipEntity m
                        where m.id.organizationId = r.organizationId and m.id.userId = :actor and m.canConfirmMembers)))
                  and (:kind is null or r.type = :kind) and (:state is null or r.status = :state)
                order by r.createdAt desc, r.id
                """,
                        OrganizationRequestEntity.class)
                .setParameter("actor", actor)
                .setParameter("mayReview", mayReview)
                .setParameter("kind", kind)
                .setParameter("state", state)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(OrganizationRequestRepositoryCustomImpl::toDto)
                .toList();
    }

    /** Сохраняет решение до обработки процесса. */
    @Override
    public void saveDecision(UUID id, UUID reviewer, String comment, boolean approved) {
        getReference(OrganizationRequestEntity.class, id)
                .recordDecision(reviewer, comment, approved);
    }

    /** Возвращает вердикт, ожидающий применения. */
    @Override
    public Optional<Boolean> findPendingApprovalByRequestId(UUID id) {
        return Optional.ofNullable(
                getReference(OrganizationRequestEntity.class, id).getPendingApproval());
    }

    /** Сохраняет окончательный итог заявки. */
    @Override
    public void completeRequest(int status, UUID organization, UUID id) {
        getReference(OrganizationRequestEntity.class, id)
                .complete(
                        OrganizationRequestStatus.fromId(status),
                        organization,
                        getCurrentUtcDateTime());
    }

    /** Создаёт запись об организации по одобренной заявке. */
    @Override
    public void insertOrganization(
            UUID organization, String name, String inn, String ogrn, String address) {
        persistAndFlush(
                new TransporterOrganizationEntity(
                        organization, name, inn, ogrn, address, getCurrentUtcDateTime()));
    }

    /** Добавляет участника, если членство ещё не было создано. */
    @Override
    public void insertMember(UUID organization, UUID user, boolean firstRepresentative) {
        // A different approved request may concurrently try to attach the same member.
        em.find(TransporterOrganizationEntity.class, organization, LockModeType.PESSIMISTIC_WRITE);
        if (!isMember(organization, user)) {
            persistAndFlush(
                    new OrganizationMembershipEntity(
                            new OrganizationMembershipEntity.Key(organization, user),
                            firstRepresentative,
                            firstRepresentative));
        }
    }

    /** Проверяет право подтверждать сотрудников выбранной организации. */
    @Override
    public boolean mayConfirm(UUID organization, UUID user) {
        var membership = member(organization, user);
        return membership != null && membership.isCanConfirmMembers();
    }

    /** Читает заявку и при необходимости блокирует её для принятия решения. */
    @Override
    public Optional<OrganizationRequestDto> findOrganizationRequestById(UUID id, boolean lock) {
        return Optional.ofNullable(
                        em.find(
                                OrganizationRequestEntity.class,
                                id,
                                lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE))
                .map(OrganizationRequestRepositoryCustomImpl::toDto);
    }

    private OrganizationMembershipEntity member(UUID organization, UUID user) {
        return em.find(
                OrganizationMembershipEntity.class,
                new OrganizationMembershipEntity.Key(organization, user));
    }

    private static OrganizationRequestDto toDto(OrganizationRequestEntity request) {
        return new OrganizationRequestDto(
                        request.getId(),
                        request.getApplicantId(),
                        request.getType().getId(),
                        request.getStatus().getId(),
                        request.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .organizationId(request.getOrganizationId())
                .decidedById(request.getDecidedById())
                .decisionComment(request.getDecisionComment())
                .organizationData(
                        request.getType() == OrganizationRequestType.REGISTRATION
                                ? new RegisterOrganization(
                                        request.getOrganizationName(),
                                        request.getInn(),
                                        request.getOgrn(),
                                        request.getLegalAddress(),
                                        request.getAuthorityEvidence())
                                : null);
    }
}
