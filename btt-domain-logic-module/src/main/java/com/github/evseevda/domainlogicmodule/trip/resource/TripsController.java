package com.github.evseevda.domainlogicmodule.trip.resource;

import com.github.evseevda.domainlogic.api.TripsApi;
import com.github.evseevda.domainlogic.model.TripDto;

import com.github.evseevda.domainlogicmodule.trip.service.TripSearch;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class TripsController implements TripsApi {
    private final ObjectProvider<TripSearch> services;

    public TripsController(ObjectProvider<TripSearch> services) {
        this.services = services;
    }


    @Override
    public ResponseEntity<TripDto> getTrip(UUID id) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<List<TripDto>> listTrips(UUID routeId, UUID transporterId, String source, String destination,
            String carrier, UUID transportKindId, OffsetDateTime departureFrom, OffsetDateTime departureUntil,
            BigDecimal maxPrice, Boolean bookableOnly, Integer offset, Integer limit) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.list(new TripSearch.Filter(routeId, transporterId, transportKindId,
                        source, destination, carrier, departureFrom, departureUntil, maxPrice, Boolean.TRUE.equals(bookableOnly)),
                        offset, limit));
    }

    @Override
    public ResponseEntity<List<TripSeatDto>> listTripSeats(UUID tripId) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.seats(tripId));
    }
}
