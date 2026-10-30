package com.wudjabudja.wudjabudja_backend.dev;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.service.GeminiService;
import com.wudjabudja.wudjabudja_backend.service.TripContextService;
import org.springframework.web.bind.annotation.*;

// TEMPORARY: lets us curl Ethan's services before the real routes exist.
// Delete this whole file once George's generate route (MVP-06) is merged.
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final TripContextService tripContextService;
    private final GeminiService geminiService;

    public DevController(TripContextService tripContextService, GeminiService geminiService) {
        this.tripContextService = tripContextService;
        this.geminiService = geminiService;
    }

    // GET /api/dev/trips/1/context -> the JSON we'd send to Gemini for trip 1
    @GetMapping("/trips/{tripId}/context")
    public TripContext tripContext(@PathVariable Long tripId) {
        return tripContextService.build(tripId);
    }

    // POST /api/dev/trips/1/itinerary -> calls Gemini for real (uses API quota). Nothing is saved.
    @PostMapping("/trips/{tripId}/itinerary")
    public Itinerary itinerary(@PathVariable Long tripId) {
        return geminiService.generateItinerary(tripContextService.build(tripId));
    }
}
