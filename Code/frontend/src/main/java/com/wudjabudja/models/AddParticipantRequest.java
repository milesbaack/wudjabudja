package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body sent to POST /api/trips/{id}/participants to add a traveler by email (GRP-02 / GRP-03).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddParticipantRequest(
        @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName
) {
    public AddParticipantRequest(String email) {
        this(email, null);
    }
}
