package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.PreferenceRequest;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import com.wudjabudja.wudjabudja_backend.model.UserPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController                  // "this class answers web requests"
@RequestMapping("/api/trips")    // every address here starts with /api/trips
public class PreferenceController {

    private final UserPreferenceRepository repository;
    private final TripRepository tripRepository;

    // Spring hands us the repositories automatically
    public PreferenceController(UserPreferenceRepository repository, TripRepository tripRepository) {
        this.repository = repository;
        this.tripRepository = tripRepository;
    }

    // Runs when someone sends POST /api/trips/{tripId}/preferences
    @PostMapping("/{tripId}/preferences")
    public ResponseEntity<Map<String, Object>> savePreferences(
            @PathVariable Long tripId,           // the number from the URL
            @RequestBody PreferenceRequest request) {  // the JSON from the form

        if (request.userId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "user_id is required"));
        }
        // A clear 404 instead of a foreign key error when the trip doesn't exist
        requireTrip(tripId);

        // Step 1: find this user's existing row for this trip, or make a new one
        UserPreference pref = repository
                .findByTripIdAndUserId(tripId, request.userId())
                .orElseGet(UserPreference::new);

        // Step 2: copy the form's answers onto it
        pref.setTripId(tripId);
        pref.setUserId(request.userId());
        if (request.wakeTime() != null) pref.setWakeTime(LocalTime.parse(request.wakeTime()));
        if (request.experienceLevel() != null) pref.setExperienceLevel(request.experienceLevel());
        if (request.maxRadiusMiles() != null) pref.setMaxRadiusMiles(request.maxRadiusMiles());
        if (request.categoricalBudgets() != null) pref.setCategoricalBudgets(request.categoricalBudgets());
        if (request.perBlockCapUsd() != null) pref.setPerBlockCapUsd(request.perBlockCapUsd());
        if (request.interests() != null) pref.setInterests(request.interests());
        pref.setUpdatedAt(OffsetDateTime.now());

        // Step 3: save it to the database and reply
        UserPreference saved = repository.save(pref);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "Preferences saved"));
    }

    // Runs when someone sends GET /api/trips/{tripId}/preferences?userId=1
    @GetMapping("/{tripId}/preferences")
    public ResponseEntity<?> loadPreferences(
            @PathVariable Long tripId,
            @RequestParam("userId") Long userId) {

        return repository.findByTripIdAndUserId(tripId, userId)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(new PreferenceRequest(
                        p.getTripId(),
                        p.getUserId(),
                        p.getWakeTime() != null ? p.getWakeTime().toString() : null,
                        p.getExperienceLevel(),
                        p.getMaxRadiusMiles(),
                        p.getCategoricalBudgets(),
                        p.getPerBlockCapUsd(),
                        p.getInterests())))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("message", "No preferences saved yet")));
    }

    private void requireTrip(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found");
        }
    }
}
