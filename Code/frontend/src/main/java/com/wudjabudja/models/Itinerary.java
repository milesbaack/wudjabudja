package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * A trip's saved itinerary, exactly as GET/POST /api/trips/{id}/itinerary returns it (MVP-09).
 * days -> blocks (morning, afternoon, evening) -> subgroups -> 3 options each.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Itinerary(
        @JsonProperty("trip_id") Long tripId,
        @JsonProperty("generated_at") String generatedAt,   // ISO time in UTC, e.g. "2026-11-05T22:30:00Z"
        @JsonProperty("days") List<Day> days
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(
            @JsonProperty("date") String date,
            @JsonProperty("blocks") List<Block> blocks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Block(
            @JsonProperty("time_block") String timeBlock,
            @JsonProperty("subgroups") List<Subgroup> subgroups
    ) {}

    // Usually one subgroup with everyone. Two when the group splits up for that block.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subgroup(
            @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,
            @JsonProperty("split_reason") String splitReason,
            @JsonProperty("options") List<Option> options
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            @JsonProperty("id") Long id,                   // database id, used for voting in Sprint 2
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("neighborhood") String neighborhood,
            @JsonProperty("start_time") String startTime,  // "09:00"
            @JsonProperty("end_time") String endTime,
            @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,   // per person
            @JsonProperty("why_it_fits") String whyItFits,
            @JsonProperty("is_final") boolean isFinal
    ) {}
}
