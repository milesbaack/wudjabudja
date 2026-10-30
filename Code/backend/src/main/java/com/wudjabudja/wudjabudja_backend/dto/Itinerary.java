package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

// Gemini's answer for one trip (MVP-05).
// Shaped exactly like resources/ai/itinerary_schema.json: days -> blocks -> subgroups -> options.
public record Itinerary(
        @JsonProperty("days") List<Day> days
) {

    // One date of the trip. Always 3 blocks: morning, afternoon, evening.
    public record Day(
            @JsonProperty("date") String date,              // "2027-03-12"
            @JsonProperty("blocks") List<Block> blocks
    ) {}

    // One time block. Usually 1 subgroup with everyone, 2 if the group splits up.
    public record Block(
            @JsonProperty("time_block") String timeBlock,   // "morning", "afternoon" or "evening"
            @JsonProperty("subgroups") List<Subgroup> subgroups
    ) {}

    // Who's doing this block together, and their 3 options to rank.
    public record Subgroup(
            @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,  // user_ids
            @JsonProperty("split_reason") String splitReason,                       // null when nobody splits
            @JsonProperty("options") List<Option> options
    ) {}

    // One activity. Matches a row in the itinerary_options table.
    public record Option(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("neighborhood") String neighborhood,
            @JsonProperty("start_time") String startTime,   // "09:00"
            @JsonProperty("end_time") String endTime,
            @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,  // per person
            @JsonProperty("why_it_fits") String whyItFits
    ) {}
}
