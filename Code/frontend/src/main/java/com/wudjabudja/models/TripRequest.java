package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * What the Trip Setup screen sends to POST /api/trips (snake_case, like the backend's TripRequest).
 * title is optional: when it's null the backend makes one like "San Diego, Mar 12 to 14".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripRequest(
        @JsonProperty("title") String title,
        @JsonProperty("destination") String destination,
        @JsonProperty("start_date") String startDate,     // "2027-03-12"
        @JsonProperty("end_date") String endDate,
        @JsonProperty("trip_type") String tripType,       // "friends_trip"
        @JsonProperty("total_budget") BigDecimal totalBudget
) {}
