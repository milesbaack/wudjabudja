package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.ItineraryResponse;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.service.GeminiService;
import com.wudjabudja.wudjabudja_backend.service.ItineraryStore;
import com.wudjabudja.wudjabudja_backend.service.TripContextService;
import com.wudjabudja.wudjabudja_backend.service.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Generate and load a trip's itinerary (MVP-06).
// Flow: trips + user_preferences -> TripContextService -> GeminiService -> ItineraryStore -> itinerary_options
@RestController
@RequestMapping("/api/trips")
public class ItineraryController {

    private final TripService tripService;
    private final TripContextService tripContextService;
    private final GeminiService geminiService;
    private final ItineraryStore itineraryStore;

    public ItineraryController(TripService tripService, TripContextService tripContextService,
                               GeminiService geminiService, ItineraryStore itineraryStore) {
        this.tripService = tripService;
        this.tripContextService = tripContextService;
        this.geminiService = geminiService;
        this.itineraryStore = itineraryStore;
    }

    // POST /api/trips/5/itinerary -> calls Gemini (up to 3 minutes), replaces any old itinerary, returns the new one.
    // Each step throws a clear error on its own: 404 no trip, 400 no preferences, 502/504 Gemini trouble.
    // No database transaction is held open while we wait on Gemini; only the save step is transactional.
    @PostMapping("/{tripId}/itinerary")
    public ItineraryResponse generateItinerary(@PathVariable Long tripId) {
        TripContext context = tripContextService.build(tripId);
        Itinerary itinerary = geminiService.generateItinerary(context);
        return itineraryStore.replaceForTrip(tripId, itinerary);
    }

    // GET /api/trips/5/itinerary -> the saved itinerary, or 404 if it hasn't been generated yet
    @GetMapping("/{tripId}/itinerary")
    public ItineraryResponse loadItinerary(@PathVariable Long tripId) {
        tripService.get(tripId);   // 404 "Trip 5 not found" if the trip itself is missing
        return itineraryStore.loadForTrip(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No itinerary yet for trip " + tripId + ". Generate one first."));
    }
}
