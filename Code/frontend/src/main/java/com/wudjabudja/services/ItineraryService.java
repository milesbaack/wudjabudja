package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wudjabudja.models.Itinerary;

import java.time.Duration;

/**
 * Generates and loads a trip's itinerary through Spring Boot (MVP-09).
 */
public class ItineraryService {

    // The backend gives Gemini up to 3 minutes, so wait a bit longer than that before giving up
    private static final Duration GENERATE_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(15);

    private final BackendClient client = new BackendClient();

    // GET /api/trips/{id}/itinerary. A 404 (isNotFound) means "not generated yet".
    public ApiResult<Itinerary> loadItinerary(Long tripId) {
        return client.get("/api/trips/" + tripId + "/itinerary", LOAD_TIMEOUT, new TypeReference<Itinerary>() {});
    }

    // POST /api/trips/{id}/itinerary. Calls Gemini, so this can take a minute or two. Never call it on the UI thread.
    public ApiResult<Itinerary> generateItinerary(Long tripId) {
        return client.post("/api/trips/" + tripId + "/itinerary", null, GENERATE_TIMEOUT, new TypeReference<Itinerary>() {});
    }
}
