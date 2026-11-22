package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

// Winning option for one time block (and subgroup) returned by GET and POST /api/trips/{tripId}/votes (VOTE-02 / VOTE-04).
public record VoteWinnerResponse(
        @JsonProperty("option_id") Long optionId,
        @JsonProperty("date") String date,
        @JsonProperty("time_block") String timeBlock,
        @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,
        @JsonProperty("split_reason") String splitReason,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("category") String category,
        @JsonProperty("neighborhood") String neighborhood,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,
        @JsonProperty("total_points") Integer totalPoints,
        @JsonProperty("vote_count") Integer voteCount
) {}
