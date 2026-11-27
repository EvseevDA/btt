package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Управляет заявлением, его решением и результатами команд Zeebe в транзакции процесса. */
public interface RegistrationRequestRepository extends JpaRepository<TransporterRequest, UUID>, RegistrationRequestRepositoryCustom {
    /** Организация и состояние перевозчика для проверки заявки. */
    record Carrier(UUID organization, int status) {
    }

    /** Внешняя карточка заявки и внутренние данные обработки. */
    record Request(TransporterRequestDto dto, String pendingAction, UUID pendingCommand, String proposedData) {
    }
}
