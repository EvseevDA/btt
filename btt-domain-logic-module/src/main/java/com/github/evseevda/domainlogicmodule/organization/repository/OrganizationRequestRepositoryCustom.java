package com.github.evseevda.domainlogicmodule.organization.repository;

import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface OrganizationRequestRepositoryCustom {
    /** Проверяет наличие организации с такими реквизитами. */
    boolean existsByInnOrOgrn(String inn, String ogrn);

    /** Сохраняет заявку на подключение. */
    void insertRegistration(
            UUID request,
            UUID applicant,
            String name,
            String inn,
            String ogrn,
            String address,
            String evidence);

    /** Проверяет, что организация подключена. */
    boolean existsOrganizationById(UUID organization);

    /** Проверяет членство пользователя в организации. */
    boolean isMember(UUID organization, UUID applicant);

    /** Сохраняет заявку на присоединение к организации. */
    void insertMembership(UUID request, UUID applicant, UUID organization);

    /** Меняет полномочия участника, если тот состоит в организации. */
    int updatePermissions(UUID organization, UUID user, boolean represent, boolean confirm);

    /** Читает доступные пользователю заявки с фильтрами по виду и состоянию. */
    List<OrganizationRequestDto> findAllVisibleRequests(
            UUID actor, boolean mayReview, Integer type, Integer status, int offset, int limit);

    /** Сохраняет решение до обработки процесса. */
    void saveDecision(UUID id, UUID reviewer, String comment, boolean approved);

    /** Возвращает вердикт, ожидающий применения. */
    Optional<Boolean> findPendingApprovalByRequestId(UUID id);

    /** Сохраняет окончательный итог заявки. */
    void completeRequest(int status, UUID organization, UUID id);

    /** Создаёт запись об организации по одобренной заявке. */
    void insertOrganization(
            UUID organization, String name, String inn, String ogrn, String address);

    /** Добавляет участника, если членство ещё не было создано. */
    void insertMember(UUID organization, UUID user, boolean firstRepresentative);

    /** Проверяет право подтверждать сотрудников выбранной организации. */
    boolean mayConfirm(UUID organization, UUID user);

    /** Читает заявку и при необходимости блокирует её для принятия решения. */
    Optional<OrganizationRequestDto> findOrganizationRequestById(UUID id, boolean lock);
}
