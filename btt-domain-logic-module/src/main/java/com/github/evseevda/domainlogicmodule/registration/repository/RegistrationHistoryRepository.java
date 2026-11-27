package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Читает доступные заявления и исторические снимки перевозчика и РУ. */
public interface RegistrationHistoryRepository extends JpaRepository<RegistrationCertificate, UUID>, RegistrationHistoryRepositoryCustom {
}
