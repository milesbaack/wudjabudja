package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * A saved trip as the backend returns it (GET/POST /api/trips).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Trip(
        @JsonProperty("id") Long id,
        @JsonProperty("creator_id") Long creatorId,
        @JsonProperty("title") String title,
        @JsonProperty("destination") String destination,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("total_budget") BigDecimal totalBudget,
        @JsonProperty("trip_type") String tripType,
        @JsonProperty("join_code") String joinCode
) {

    private static final DateTimeFormatter NICE_DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);

    // Mar 12 to Mar 14 counts as 3 days
    public long numDays() {
        return ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + 1;
    }

    // "Fri, Mar 12, 2027 to Sun, Mar 14, 2027"
    public String dateRangeText() {
        return LocalDate.parse(startDate).format(NICE_DATE) + " to " + LocalDate.parse(endDate).format(NICE_DATE);
    }
}
