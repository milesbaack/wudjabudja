package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Describes the JSON the form sends. Each line = one answer from the form.
public record PreferenceRequest(
        @JsonProperty("trip_id") Long tripId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("wake_time") String wakeTime,
        @JsonProperty("experience_level") String experienceLevel,
        @JsonProperty("max_radius_miles") Integer maxRadiusMiles,
        @JsonProperty("categorical_budgets") Map<String, Double> categoricalBudgets,
        @JsonProperty("per_block_cap_usd") BigDecimal perBlockCapUsd,
        @JsonProperty("interests") List<String> interests
) {}