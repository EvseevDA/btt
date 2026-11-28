package com.github.evseevda.domainlogicmodule.workflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestType;
import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateVersionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionStatus;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import com.github.evseevda.domainlogicmodule.ticket.conversion.TicketStatusConverter;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterLicenseStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import com.github.evseevda.domainlogicmodule.trip.conversion.TripStatusConverter;
import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;
import jakarta.persistence.AttributeConverter;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class EnumConvertersTest {
    @TestFactory
    Stream<DynamicTest> roundTripsDatabaseCodes() {
        return Stream.of(
                verifyConverter(
                        OrganizationRequestStatus.class, new OrganizationRequestStatusConverter()),
                verifyConverter(
                        OrganizationRequestType.class, new OrganizationRequestTypeConverter()),
                verifyConverter(
                        RegistrationCertificateStatus.class,
                        new RegistrationCertificateStatusConverter()),
                verifyConverter(
                        RegistrationCertificateVersionStatus.class,
                        new RegistrationCertificateVersionStatusConverter()),
                verifyConverter(DecisionStatus.class, new DecisionStatusConverter()),
                verifyConverter(DecisionType.class, new DecisionTypeConverter()),
                verifyConverter(
                        TransporterRequestStatus.class, new TransporterRequestStatusConverter()),
                verifyConverter(
                        TransporterRequestType.class, new TransporterRequestTypeConverter()),
                verifyConverter(TicketStatus.class, new TicketStatusConverter()),
                verifyConverter(
                        TransporterLicenseStatus.class, new TransporterLicenseStatusConverter()),
                verifyConverter(TransporterStatus.class, new TransporterStatusConverter()),
                verifyConverter(TripStatus.class, new TripStatusConverter()));
    }

    private static <E extends Enum<E>> DynamicTest verifyConverter(
            Class<E> enumType, AttributeConverter<E, Integer> converter) {
        return DynamicTest.dynamicTest(
                converter.getClass().getSimpleName(),
                () -> checkDatabaseCodes(enumType, converter));
    }

    private static <E extends Enum<E>> void checkDatabaseCodes(
            Class<E> enumType, AttributeConverter<E, Integer> converter) {
        var knownIds = new HashSet<Integer>();
        for (var constant : enumType.getEnumConstants()) {
            Integer id = converter.convertToDatabaseColumn(constant);
            assertNotNull(id);
            assertTrue(knownIds.add(id), "Database IDs must be unique");
            assertSame(constant, converter.convertToEntityAttribute(id));
        }
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToDatabaseColumn(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToEntityAttribute(Integer.MIN_VALUE));
    }
}
