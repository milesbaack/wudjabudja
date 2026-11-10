package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

// The JSON the Trip Setup screen sends to POST /api/trips.
// Dates are plain strings ("2027-03-12") so a typo gets a clear 400 instead of a parse crash.
public record TripRequest(
        @JsonProperty("title") String title,                // optional, we make one if blank
        @JsonProperty("destination") String destination,    // "San Diego"
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("trip_type") String tripType,         // "friends_trip", etc.
        @JsonProperty("total_budget") BigDecimal totalBudget
) {}
