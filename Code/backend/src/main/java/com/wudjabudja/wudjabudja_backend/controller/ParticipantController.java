package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.AddParticipantRequest;
import com.wudjabudja.wudjabudja_backend.dto.ParticipantResponse;
import com.wudjabudja.wudjabudja_backend.service.ParticipantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Add, list, and remove travelers on a shared trip (GRP-02).
@RestController
@RequestMapping("/api/trips")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    // GET /api/trips/5/participants -> travelers on the trip with their role and quiz_saved flag
    @GetMapping("/{tripId}/participants")
    public List<ParticipantResponse> listParticipants(@PathVariable Long tripId) {
        return participantService.listForTrip(tripId);
    }

    // POST /api/trips/5/participants -> adds a traveler by email as a member (creating the users row if new)
    @PostMapping("/{tripId}/participants")
    public ResponseEntity<ParticipantResponse> addParticipant(
            @PathVariable Long tripId,
            @RequestBody AddParticipantRequest request) {
        ParticipantResponse created = participantService.addByEmail(tripId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // DELETE /api/trips/5/participants/2 -> removes a member traveler from the trip
    @DeleteMapping("/{tripId}/participants/{userId}")
    public ResponseEntity<Map<String, Object>> removeParticipant(
            @PathVariable Long tripId,
            @PathVariable Long userId) {
        participantService.removeParticipant(tripId, userId);
        return ResponseEntity.ok(Map.of("user_id", userId, "message", "Traveler removed from trip"));
    }
}
