package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Сохраняет исторические снимки перевозчика, РУ и лицензий. */
public interface RegistrationSnapshotRepository extends JpaRepository<TransporterVersion, UUID>, RegistrationSnapshotRepositoryCustom {
}
