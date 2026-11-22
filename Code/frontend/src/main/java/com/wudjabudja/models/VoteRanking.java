package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One ranked choice in a traveler's Borda Count ballot (VOTE-01 / VOTE-02).
 * 1st choice = 3 pts, 2nd choice = 2 pts, 3rd choice = 1 pt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoteRanking(
        @JsonProperty("option_id") @JsonAlias({"optionId"}) Long optionId,
        @JsonProperty("rank") int rank,
        @JsonProperty("points") int points
) {
    public static VoteRanking of(Long optionId, int rank) {
        int pts = Math.max(1, 4 - rank);
        return new VoteRanking(optionId, rank, pts);
    }
}
