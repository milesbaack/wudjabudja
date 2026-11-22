package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.TripBriefing;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import com.wudjabudja.wudjabudja_backend.service.GeminiService;
import com.wudjabudja.wudjabudja_backend.service.TripContextService;
import com.wudjabudja.wudjabudja_backend.service.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Generate and load a trip's pre-departure cultural, language, packing and souvenir briefing (BRIEF-01 / BRIEF-02).
@RestController
@RequestMapping("/api/trips")
public class BriefingController {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final TripContextService tripContextService;
    private final GeminiService geminiService;

    public BriefingController(TripService tripService,
                              TripRepository tripRepository,
                              TripContextService tripContextService,
                              GeminiService geminiService) {
        this.tripService = tripService;
        this.tripRepository = tripRepository;
        this.tripContextService = tripContextService;
        this.geminiService = geminiService;
    }

    // POST /api/trips/5/briefing -> calls Gemini with briefing_system.txt + briefing_schema.json,
    // saves the result into trips.briefing_data JSONB, and returns it.
    @PostMapping("/{tripId}/briefing")
    public TripBriefing generateBriefing(@PathVariable Long tripId) {
        TripContext context = tripContextService.buildForBriefing(tripId);
        TripBriefing briefing = geminiService.generateBriefing(context);
        Trip trip = tripService.get(tripId);
        trip.setBriefingData(briefing);
        tripRepository.save(trip);
        return briefing;
    }

    // GET /api/trips/5/briefing -> returns the saved briefing from trips.briefing_data, or 404 if not generated yet.
    @GetMapping("/{tripId}/briefing")
    public TripBriefing loadBriefing(@PathVariable Long tripId) {
        Trip trip = tripService.get(tripId);
        if (trip.getBriefingData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No trip briefing generated yet for trip " + tripId + ". Generate one first.");
        }
        return trip.getBriefingData();
    }
}
