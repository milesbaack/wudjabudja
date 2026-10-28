package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.PreferenceRequest;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import com.wudjabudja.wudjabudja_backend.model.UserPreferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController                  // "this class answers web requests"
@RequestMapping("/api/trips")    // every address here starts with /api/trips
public class PreferenceController {

    private final UserPreferenceRepository repository;

    // Spring hands us the repository automatically
    public PreferenceController(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    // Runs when someone sends POST /api/trips/{tripId}/preferences
    @PostMapping("/{tripId}/preferences")
    public ResponseEntity<Map<String, Object>> savePreferences(
            @PathVariable Long tripId,           // the number from the URL
            @RequestBody PreferenceRequest request) {  // the JSON from the form

        if (request.userId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "user_id is required"));
        }

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
}