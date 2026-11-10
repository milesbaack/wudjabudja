package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.TripRequest;
import com.wudjabudja.wudjabudja_backend.dto.TripResponse;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

// Create and look up trips (TRIP-01). The rules live in TripService; this class just maps URLs.
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // POST /api/trips -> 201 Created with the new trip (including its id), or 400 with a message
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@RequestBody TripRequest request) {
        Trip saved = tripService.create(request);
        return ResponseEntity
                .created(URI.create("/api/trips/" + saved.getId()))   // sets the Location header
                .body(TripResponse.from(saved));
    }

    // GET /api/trips/5 -> the trip, or 404
    @GetMapping("/{id}")
    public TripResponse getTrip(@PathVariable Long id) {
        return TripResponse.from(tripService.get(id));
    }

    // GET /api/trips -> user 1's trips, newest first (so the app can reopen an older trip)
    @GetMapping
    public List<TripResponse> listTrips() {
        return tripService.listForDemoUser().stream().map(TripResponse::from).toList();
    }
}
