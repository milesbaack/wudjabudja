package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Request body for POST /api/trips/{tripId}/votes (VOTE-02).
public record VoteSubmissionRequest(
        @JsonProperty("user_id") @JsonAlias({"userId"}) Long userId,
        @JsonProperty("rankings") List<RankingItem> rankings,
        @JsonProperty("votes") List<RankingItem> votes
) {
    public VoteSubmissionRequest(Long userId, List<RankingItem> rankings) {
        this(userId, rankings, null);
    }

    // Accepts either "rankings" or "votes" in the JSON body
    public List<RankingItem> items() {
        if (rankings != null && !rankings.isEmpty()) return rankings;
        return votes;
    }

    public record RankingItem(
            @JsonProperty("option_id") @JsonAlias({"optionId"}) Long optionId,
            @JsonProperty("rank") Integer rank,
            @JsonProperty("points") Integer points
    ) {
        public RankingItem(Long optionId, Integer rank) {
            this(optionId, rank, rank == null ? null : Math.max(1, 4 - rank));
        }
    }
}
