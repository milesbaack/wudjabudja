package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Everything Gemini needs to know about one trip (MVP-04).
// Shaped exactly like Code/ai-pipeline/samples/trip_context.json.
public record TripContext(
        @JsonProperty("trip_id") Long tripId,
        @JsonProperty("destination") String destination,
        @JsonProperty("start_date") String startDate,   // "2027-03-12"
        @JsonProperty("end_date") String endDate,
        @JsonProperty("num_days") int numDays,
        @JsonProperty("trip_type") String tripType,
        @JsonProperty("total_budget") BigDecimal totalBudget,
        @JsonProperty("participants") List<Participant> participants
) {

    // One traveler's quiz answers. One of these per row in user_preferences.
    public record Participant(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("role") String role,
            @JsonProperty("wake_time") String wakeTime,  // "07:30"
            @JsonProperty("experience_level") String experienceLevel,
            @JsonProperty("max_radius_miles") Integer maxRadiusMiles,
            @JsonProperty("categorical_budgets") Map<String, Double> categoricalBudgets,
            @JsonProperty("per_block_cap_usd") BigDecimal perBlockCapUsd,
            @JsonProperty("interests") List<String> interests
    ) {}
}
