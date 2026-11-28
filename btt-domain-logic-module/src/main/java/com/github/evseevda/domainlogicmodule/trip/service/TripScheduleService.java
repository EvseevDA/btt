package com.github.evseevda.domainlogicmodule.trip.service;

import com.github.evseevda.domainlogic.model.CreateTrip;
import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogicmodule.catalog.repository.RouteRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransporterRepository;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembershipEntity.Key;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationMembershipRepository;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import com.github.evseevda.domainlogicmodule.trip.domain.TripEntity;
import com.github.evseevda.domainlogicmodule.trip.domain.TripSeatEntity;
import com.github.evseevda.domainlogicmodule.trip.repository.TripRepository;
import com.github.evseevda.domainlogicmodule.trip.repository.TripSeatRepository;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Публикует рейсы активных перевозчиков вместе с доступными местами. */
@Service
@Profile("!mock-api")
public class TripScheduleService {
    private final TransporterRepository transporters;
    private final RouteRepository routes;
    private final OrganizationMembershipRepository memberships;
    private final TripRepository trips;
    private final TripSeatRepository seats;

    /** Подключает справочники, членства и расписание. */
    public TripScheduleService(
            TransporterRepository transporters,
            RouteRepository routes,
            OrganizationMembershipRepository memberships,
            TripRepository trips,
            TripSeatRepository seats) {
        this.transporters = transporters;
        this.routes = routes;
        this.memberships = memberships;
        this.trips = trips;
        this.seats = seats;
    }

    /** Проверяет полномочия представителя и публикует рейс доступного ему перевозчика. */
    @Transactional
    public TripDto createTrip(CreateTrip form) {
        var user = CurrentAccount.getUserId();
        var carrier =
                transporters
                        .findByIdForSchedulingWithLock(form.getTransporterId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Перевозчик не найден"));
        if (memberships
                .findById(new Key(carrier.getOrganization().getId(), user))
                .filter(member -> member.isCanRepresent())
                .isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Публиковать рейсы может представитель перевозчика");
        }
        if (carrier.getStatus() != TransporterStatus.REGISTERED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Регистрация перевозчика должна действовать");
        }
        var route =
                routes.findById(form.getRouteId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Маршрут не найден"));
        if (!route.getTransportKind().getId().equals(carrier.getTransportKind().getId())) {
            throw bad("Вид транспорта маршрута отличается от вида транспорта перевозчика");
        }
        if (!form.getDepartureAt().toInstant().isAfter(Instant.now())
                || !form.getArrivalAt().isAfter(form.getDepartureAt())) {
            throw bad("Рейс должен отправляться в будущем и прибывать после отправления");
        }
        if (form.getSeatCodes().stream().anyMatch(Objects::isNull)) {
            throw bad("Коды мест должны быть непустыми и уникальными");
        }
        var codes = form.getSeatCodes().stream().map(String::strip).distinct().toList();
        if (codes.size() != form.getSeatCodes().size()
                || codes.stream().anyMatch(String::isEmpty)) {
            throw bad("Коды мест должны быть непустыми и уникальными");
        }
        var price = form.getBasePrice();
        if (price.stripTrailingZeros().scale() > 2) {
            throw bad("Допускается не более двух знаков после запятой");
        }
        var trip =
                trips.save(
                        new TripEntity(
                                route,
                                carrier,
                                form.getDepartureAt()
                                        .withOffsetSameInstant(ZoneOffset.UTC)
                                        .toLocalDateTime(),
                                form.getArrivalAt()
                                        .withOffsetSameInstant(ZoneOffset.UTC)
                                        .toLocalDateTime(),
                                price.setScale(2, RoundingMode.UNNECESSARY)));
        seats.saveAll(codes.stream().map(code -> new TripSeatEntity(trip.getId(), code)).toList());
        return trips.findTripById(trip.getId()).orElseThrow();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
