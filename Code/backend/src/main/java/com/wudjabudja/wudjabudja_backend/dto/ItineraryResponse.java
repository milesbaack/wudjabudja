package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

// A saved itinerary as the app sees it (MVP-06/07): the same days -> blocks -> subgroups -> options
// shape as Itinerary, plus each option's database id (votes need it later) and when it was made.
public record ItineraryResponse(
        @JsonProperty("trip_id") Long tripId,
        @JsonProperty("generated_at") String generatedAt,   // ISO timestamp, e.g. "2026-11-05T14:30:00-08:00"
        @JsonProperty("days") List<Day> days
) {

    public record Day(
            @JsonProperty("date") String date,
            @JsonProperty("blocks") List<Block> blocks
    ) {}

    public record Block(
            @JsonProperty("time_block") String timeBlock,
            @JsonProperty("subgroups") List<Subgroup> subgroups
    ) {}

    public record Subgroup(
            @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,
            @JsonProperty("split_reason") String splitReason,
            @JsonProperty("options") List<Option> options
    ) {}

    public record Option(
            @JsonProperty("id") Long id,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("neighborhood") String neighborhood,
            @JsonProperty("start_time") String startTime,   // "09:00", or null if Gemini's time was unreadable
            @JsonProperty("end_time") String endTime,
            @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,
            @JsonProperty("why_it_fits") String whyItFits,
            @JsonProperty("is_final") boolean isFinal
    ) {}
}
