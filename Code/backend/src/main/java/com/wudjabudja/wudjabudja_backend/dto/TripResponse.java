package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import java.math.BigDecimal;
import java.math.RoundingMode;

// What the trip routes send back. Same snake_case names as the trips table.
public record TripResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("creator_id") Long creatorId,
        @JsonProperty("title") String title,
        @JsonProperty("destination") String destination,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("total_budget") BigDecimal totalBudget,
        @JsonProperty("trip_type") String tripType,
        @JsonProperty("join_code") String joinCode
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.getId(), trip.getCreatorId(), trip.getTitle(), trip.getDestination(),
                trip.getStartDate().toString(), trip.getEndDate().toString(),
                // Always 2 decimals like the NUMERIC(10, 2) column, so a fresh POST and a later GET match
                trip.getTotalBudget().setScale(2, RoundingMode.HALF_UP),
                trip.getTripType(), trip.getJoinCode());
    }
}
