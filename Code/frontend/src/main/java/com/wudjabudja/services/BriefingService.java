package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wudjabudja.models.TripBriefing;

import java.time.Duration;

/**
 * Generates and loads a trip's pre-departure briefing through Spring Boot (BRIEF-01..03).
 */
public class BriefingService {

    private static final Duration GENERATE_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(15);

    private final BackendClient client = new BackendClient();

    /**
     * GET /api/trips/{id}/briefing. A 404 (isNotFound) means "not generated yet".
     */
    public ApiResult<TripBriefing> loadBriefing(Long tripId) {
        return client.get("/api/trips/" + tripId + "/briefing", LOAD_TIMEOUT, new TypeReference<TripBriefing>() {});
    }

    /**
     * POST /api/trips/{id}/briefing. Calls Gemini to generate customs, phrases, packing list,
     * historical facts, crowd advisory, and personalized souvenirs.
     */
    public ApiResult<TripBriefing> generateBriefing(Long tripId) {
        return client.post("/api/trips/" + tripId + "/briefing", null, GENERATE_TIMEOUT, new TypeReference<TripBriefing>() {});
    }
}
