package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripRequest;

import java.time.Duration;
import java.util.List;

/**
 * Creates and loads trips through Spring Boot (MVP-11).
 */
public class TripService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final BackendClient client = new BackendClient();

    // POST /api/trips -> the saved trip, including its new id
    public ApiResult<Trip> createTrip(TripRequest request) {
        return client.post("/api/trips", request, TIMEOUT, new TypeReference<Trip>() {});
    }

    // GET /api/trips/{id}
    public ApiResult<Trip> getTrip(Long tripId) {
        if (DemoTripSupport.isDemoTrip(tripId) && DemoTripSupport.getDemoTrip() != null) {
            return ApiResult.ok(200, DemoTripSupport.getDemoTrip());
        }
        return client.get("/api/trips/" + tripId, TIMEOUT, new TypeReference<Trip>() {});
    }

    // GET /api/trips -> user 1's trips, newest first
    public ApiResult<List<Trip>> listTrips() {
        return client.get("/api/trips", TIMEOUT, new TypeReference<List<Trip>>() {});
    }
}
