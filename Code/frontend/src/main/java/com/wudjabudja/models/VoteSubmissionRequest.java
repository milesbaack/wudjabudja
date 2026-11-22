package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Payload sent to POST /api/trips/{id}/votes for one traveler's ranked choices (VOTE-01 / VOTE-02).
 * Includes both "rankings" and "votes" keys with the same list so the backend controller can bind
 * whichever property name it prefers.
 */
public record VoteSubmissionRequest(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("rankings") List<VoteRanking> rankings,
        @JsonProperty("votes") List<VoteRanking> votes
) {
    public VoteSubmissionRequest(Long userId, List<VoteRanking> rankings) {
        this(userId, rankings, rankings);
    }
}
