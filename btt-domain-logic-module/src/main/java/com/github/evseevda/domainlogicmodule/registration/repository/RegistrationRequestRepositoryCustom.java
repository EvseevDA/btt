package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Carrier;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Request;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface RegistrationRequestRepositoryCustom {
    /** Ищет организацию и текущее состояние перевозчика. */
    Optional<Carrier> findCarrier(UUID transporter);

    /** Сохраняет черновик с исходной версией перевозчика и предложенным снимком. */
    void insertRequest(UUID id, UUID transporter, UUID organization, UUID source, String data, UUID actor, int type);

    /** Блокирует перевозчика на время проверки другой открытой заявки. */
    void lockCarrier(UUID transporter);

    /** Проверяет, рассматривается ли уже заявление по перевозчику. */
    boolean hasOpenRequest(UUID transporter);

    /** Фиксирует подачу заявления. */
    void markSubmitted(UUID id);

    /** Создаёт решение или сохраняет новую редакцию существующего проекта. */
    void saveDraftDecision(UUID decision, UUID id, UUID actor, int type, String comment, String description);

    /** Читает результат уже обработанной команды, включая результат без решения. */
    Optional<Integer> processedDecisionType(UUID command, UUID id);

    /** Назначает исполнителя и переводит заявку на рассмотрение. */
    void assignExecutor(UUID executor, UUID id);

    /** Сохраняет комментарий к возврату решения. */
    void saveReturnReason(String reason, UUID id);

    /** Фиксирует результат команды для защиты от повторной обработки. */
    void insertProcessedCommand(UUID command, UUID id, Integer result);

    /** Убирает ссылку на выполненную команду. */
    void clearPendingAction(UUID id);

    /** Читает описание изменений из решения. */
    Optional<String> findChangeDescription(UUID id);

    /** Связывает заявление с зарегистрированным перевозчиком. */
    void attachCarrier(UUID transporter, UUID id);

    /** Сохраняет согласовавшего и время итогового решения. */
    void markDecisionApproved(UUID reviewer, UUID id);

    /** Связывает ожидающую команду с заявлением. */
    void savePendingAction(UUID id, String action, UUID command);

    /** Сохраняет новое состояние заявления. */
    void updateStatus(int status, UUID id);

    /** Читает текущую редакцию решения. */
    Optional<DecisionDto> findDecision(UUID id);

    /** Читает заявление с блокировкой на запись для переходов процесса. */
    Optional<Request> findRequest(UUID id, boolean lock);

    /** Проверяет право пользователя представлять организацию. */
    boolean mayRepresent(UUID org, UUID user);

    /** Проверяет разрешение исполнителя по его ролям. */
    boolean hasPermission(UUID user, String permission);
}
