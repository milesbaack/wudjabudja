package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

// Request body for POST /api/trips/{tripId}/participants (GRP-02).
public record AddParticipantRequest(
        @JsonProperty("email") String email,
        @JsonProperty("full_name") @JsonAlias({"fullName", "name"}) String fullName
) {
    public AddParticipantRequest(String email) {
        this(email, null);
    }
}
