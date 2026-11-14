package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * A winning time-block option returned by GET /api/trips/{id}/votes (VOTE-01 / VOTE-02 / VOTE-04).
 * Uses @JsonAlias to tolerate common field name variations from the backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoteWinner(
        @JsonProperty("option_id") @JsonAlias({"optionId", "id", "winner_option_id", "winnerOptionId"}) Long optionId,
        @JsonProperty("date") String date,
        @JsonProperty("time_block") @JsonAlias({"timeBlock", "block"}) String timeBlock,
        @JsonProperty("subgroup_participants") @JsonAlias({"subgroupParticipants"}) List<Long> subgroupParticipants,
        @JsonProperty("split_reason") @JsonAlias({"splitReason"}) String splitReason,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("category") String category,
        @JsonProperty("neighborhood") String neighborhood,
        @JsonProperty("start_time") @JsonAlias({"startTime"}) String startTime,
        @JsonProperty("end_time") @JsonAlias({"endTime"}) String endTime,
        @JsonProperty("estimated_cost_usd") @JsonAlias({"estimatedCostUsd", "cost"}) BigDecimal estimatedCostUsd,
        @JsonProperty("total_points") @JsonAlias({"totalPoints", "points", "borda_points", "bordaPoints", "score"}) Integer totalPoints,
        @JsonProperty("vote_count") @JsonAlias({"voteCount", "votes"}) Integer voteCount
) {}
