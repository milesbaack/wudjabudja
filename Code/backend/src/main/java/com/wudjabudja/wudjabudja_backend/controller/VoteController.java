package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteWinnerResponse;
import com.wudjabudja.wudjabudja_backend.service.VoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Submit traveler rankings and load Borda Count winners for a trip (VOTE-02).
@RestController
@RequestMapping("/api/trips")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    // POST /api/trips/5/votes -> saves one traveler's rankings, runs the Borda tally, and returns the winning options
    @PostMapping("/{tripId}/votes")
    public List<VoteWinnerResponse> submitVotes(
            @PathVariable Long tripId,
            @RequestBody VoteSubmissionRequest request) {
        return voteService.submitVotes(tripId, request);
    }

    // GET /api/trips/5/votes -> returns each time block's winning option (or [] if no votes yet)
    @GetMapping("/{tripId}/votes")
    public List<VoteWinnerResponse> loadWinners(@PathVariable Long tripId) {
        return voteService.loadWinners(tripId);
    }
}
