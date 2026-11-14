package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A traveler on a shared trip, as returned by GET/POST /api/trips/{id}/participants (GRP-02 / GRP-03).
 * Uses @JsonAlias so the frontend tolerates either snake_case or camelCase field names from the backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Participant(
        @JsonProperty("user_id") @JsonAlias({"userId", "id"}) Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("full_name") @JsonAlias({"fullName", "name"}) String fullName,
        @JsonProperty("role") String role,
        @JsonProperty("quiz_saved") @JsonAlias({
                "quizSaved", "has_preferences", "hasPreferences", "preferences_saved", "preferencesSaved"
        }) boolean quizSaved
) {

    public boolean isLeader() {
        return "leader".equalsIgnoreCase(role);
    }

    /**
     * Short readable label for chips and subgroup headers (e.g. "Demo Traveler" or "alex@example.com").
     */
    public String shortName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "Traveler " + (userId != null ? userId : "?");
    }

    /**
     * Detailed display label for dropdowns and roster rows.
     */
    public String displayLabel() {
        String base;
        if (fullName != null && !fullName.isBlank() && email != null && !email.isBlank()
                && !fullName.trim().equalsIgnoreCase(email.trim())) {
            base = fullName.trim() + " (" + email.trim() + ")";
        } else if (email != null && !email.isBlank()) {
            base = email.trim();
        } else if (fullName != null && !fullName.isBlank()) {
            base = fullName.trim();
        } else {
            base = "Traveler #" + (userId != null ? userId : "?");
        }
        String roleTag = isLeader() ? " · Leader" : "";
        String quizTag = quizSaved ? " · ✓ Quiz saved" : " · Quiz pending";
        return base + roleTag + quizTag;
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
