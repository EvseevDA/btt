package com.github.evseevda.domainlogicmodule.workflow;

import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateVersionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.ticket.conversion.TicketStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterLicenseStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterStatusConverter;
import com.github.evseevda.domainlogicmodule.trip.conversion.TripStatusConverter;
import jakarta.persistence.AttributeConverter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EnumConvertersTest {
    @TestFactory
    Stream<DynamicTest> roundTripsDatabaseCodes() {
        return List.<AttributeConverter<?, Integer>>of(new OrganizationRequestStatusConverter(),
                new OrganizationRequestTypeConverter(), new RegistrationCertificateStatusConverter(),
                new RegistrationCertificateVersionStatusConverter(), new DecisionStatusConverter(),
                new DecisionTypeConverter(), new TransporterRequestStatusConverter(), new TransporterRequestTypeConverter(),
                new TicketStatusConverter(), new TransporterLicenseStatusConverter(), new TransporterStatusConverter(),
                new TripStatusConverter()).stream().map(converter -> DynamicTest.dynamicTest(
                        converter.getClass().getSimpleName(), () -> check(converter)));
    }

    @SuppressWarnings("unchecked")
    private static <E> void check(AttributeConverter<E, Integer> converter) {
        var signature = (ParameterizedType) converter.getClass().getGenericInterfaces()[0];
        var enumeration = (Class<E>) signature.getActualTypeArguments()[0];
        var knownIds = new HashSet<Integer>();
        for (var constant : enumeration.getEnumConstants()) {
            Integer id = converter.convertToDatabaseColumn(constant);
            assertNotNull(id);
            assertTrue(knownIds.add(id), "Database IDs must be unique");
            assertSame(constant, converter.convertToEntityAttribute(id));
        }
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToDatabaseColumn(null));
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(Integer.MIN_VALUE));
    }
}
