package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// One traveler on a trip returned by GET and POST /api/trips/{tripId}/participants (GRP-02).
public record ParticipantResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("role") String role,
        @JsonProperty("quiz_saved") boolean quizSaved
) {}
