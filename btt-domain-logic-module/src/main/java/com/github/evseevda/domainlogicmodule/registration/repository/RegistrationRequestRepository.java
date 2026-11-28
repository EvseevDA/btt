package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Управляет заявлением, его решением и результатами команд Zeebe в транзакции процесса. */
public interface RegistrationRequestRepository
        extends JpaRepository<TransporterRequestEntity, UUID>, RegistrationRequestRepositoryCustom {
    /** Организация и состояние перевозчика для проверки заявки. */
    record TransporterRegistrationProjection(UUID organization, int status) {}

    /** Внешняя карточка заявки и внутренние данные обработки. */
    record RegistrationRequestProjection(
            TransporterRequestDto dto,
            String pendingAction,
            UUID pendingCommand,
            String proposedData) {}
}
