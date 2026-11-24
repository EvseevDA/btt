package com.github.evseevda.domainlogicmodule.trip.resource;

import com.github.evseevda.domainlogic.api.TripsApi;
import com.github.evseevda.domainlogic.model.TripDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TripsController implements TripsApi {

    @Override
    public ResponseEntity<TripDto> getTrip(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TripDto>> listTrips(UUID routeId, UUID transporterId, Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
