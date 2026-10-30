package com.wudjabudja.wudjabudja_backend.dev;

import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.service.TripContextService;
import org.springframework.web.bind.annotation.*;

// TEMPORARY: lets us curl Ethan's services before the real routes exist.
// Delete this whole file once George's generate route (MVP-06) is merged.
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final TripContextService tripContextService;

    public DevController(TripContextService tripContextService) {
        this.tripContextService = tripContextService;
    }

    // GET /api/dev/trips/1/context -> the JSON we'd send to Gemini for trip 1
    @GetMapping("/trips/{tripId}/context")
    public TripContext tripContext(@PathVariable Long tripId) {
        return tripContextService.build(tripId);
    }
}
