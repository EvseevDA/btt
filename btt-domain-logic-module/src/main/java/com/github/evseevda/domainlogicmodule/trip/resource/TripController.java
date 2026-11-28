package com.github.evseevda.domainlogicmodule.trip.resource;

import com.github.evseevda.domainlogic.api.TripsApi;
import com.github.evseevda.domainlogic.model.CreateTrip;
import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import com.github.evseevda.domainlogicmodule.trip.service.TripReadService;
import com.github.evseevda.domainlogicmodule.trip.service.TripScheduleService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Предоставляет REST-доступ к расписанию и местам рейсов. */
@RestController
public class TripController implements TripsApi {
    private final ObjectProvider<TripReadService> services;
    private final ObjectProvider<TripScheduleService> scheduling;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public TripController(
            ObjectProvider<TripReadService> services,
            ObjectProvider<TripScheduleService> scheduling) {
        this.services = services;
        this.scheduling = scheduling;
    }

    /** Возвращает рейс с тарифом и числом доступных мест. */
    @Override
    public ResponseEntity<TripDto> getTrip(UUID id) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.getTripById(id));
    }

    /** Ищет рейсы по маршруту, перевозчику, времени отправления, тарифу и доступности. */
    @Override
    public ResponseEntity<List<TripDto>> listTrips(
            UUID routeId,
            UUID transporterId,
            String source,
            String destination,
            String carrier,
            UUID transportKindId,
            OffsetDateTime departureFrom,
            OffsetDateTime departureUntil,
            BigDecimal maxPrice,
            Boolean bookableOnly,
            Integer offset,
            Integer limit) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(
                        service.findAllTrips(
                                new TripReadService.Filter(
                                        routeId,
                                        transporterId,
                                        transportKindId,
                                        source,
                                        destination,
                                        carrier,
                                        departureFrom,
                                        departureUntil,
                                        maxPrice,
                                        Boolean.TRUE.equals(bookableOnly)),
                                offset,
                                limit));
    }

    /** Возвращает список мест рейса с признаками доступности. */
    @Override
    public ResponseEntity<List<TripSeatDto>> listTripSeats(UUID tripId) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.findAllSeatsByTripId(tripId));
    }

    /** Сохраняет запись, созданную уполномоченным пользователем. */
    @Override
    public ResponseEntity<TripDto> createTrip(CreateTrip form) {
        var service = scheduling.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.createTrip(form));
    }
}
