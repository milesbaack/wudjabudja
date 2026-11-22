package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gemini's pre-trip destination briefing returned by GET and POST /api/trips/{id}/briefing (BRIEF-01..03).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripBriefing(
        @JsonProperty("customs") List<CustomTip> customs,
        @JsonProperty("language") LanguageGuide language,
        @JsonProperty("weather_note") @JsonAlias({"weatherNote"}) String weatherNote,
        @JsonProperty("packing_list") @JsonAlias({"packingList"}) List<PackingItem> packingList,
        @JsonProperty("historical_facts") @JsonAlias({"historicalFacts"}) List<String> historicalFacts,
        @JsonProperty("season_advisory") @JsonAlias({"seasonAdvisory"}) SeasonAdvisory seasonAdvisory,
        @JsonProperty("souvenirs") List<SouvenirIdea> souvenirs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomTip(
            @JsonProperty("tip") String tip,
            @JsonProperty("why") String why
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LanguageGuide(
            @JsonProperty("primary_language") @JsonAlias({"primaryLanguage"}) String primaryLanguage,
            @JsonProperty("phrases") List<Phrase> phrases
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Phrase(
            @JsonProperty("phrase") String phrase,
            @JsonProperty("meaning") String meaning
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PackingItem(
            @JsonProperty("item") String item,
            @JsonProperty("reason") String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonAdvisory(
            @JsonProperty("crowd_level") @JsonAlias({"crowdLevel"}) String crowdLevel,
            @JsonProperty("summary") String summary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SouvenirIdea(
            @JsonProperty("user_id") @JsonAlias({"userId"}) Long userId,
            @JsonProperty("idea") String idea,
            @JsonProperty("estimated_cost_usd") @JsonAlias({"estimatedCostUsd"}) BigDecimal estimatedCostUsd
    ) {}
}
