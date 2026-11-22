package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

// Gemini's pre-trip briefing JSON, matching briefing_schema.json and stored in trips.briefing_data (BRIEF-01 / BRIEF-02).
public record TripBriefing(
        @JsonProperty("customs") List<CustomTip> customs,
        @JsonProperty("language") LanguageGuide language,
        @JsonProperty("weather_note") String weatherNote,
        @JsonProperty("packing_list") List<PackingItem> packingList,
        @JsonProperty("historical_facts") List<String> historicalFacts,
        @JsonProperty("season_advisory") SeasonAdvisory seasonAdvisory,
        @JsonProperty("souvenirs") List<SouvenirIdea> souvenirs
) {
    public record CustomTip(
            @JsonProperty("tip") String tip,
            @JsonProperty("why") String why
    ) {}

    public record LanguageGuide(
            @JsonProperty("primary_language") String primaryLanguage,
            @JsonProperty("phrases") List<Phrase> phrases
    ) {}

    public record Phrase(
            @JsonProperty("phrase") String phrase,
            @JsonProperty("meaning") String meaning
    ) {}

    public record PackingItem(
            @JsonProperty("item") String item,
            @JsonProperty("reason") String reason
    ) {}

    public record SeasonAdvisory(
            @JsonProperty("crowd_level") String crowdLevel,
            @JsonProperty("summary") String summary
    ) {}

    public record SouvenirIdea(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("idea") String idea,
            @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd
    ) {}
}
