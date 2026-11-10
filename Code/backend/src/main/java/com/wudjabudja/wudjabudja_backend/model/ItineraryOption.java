package com.wudjabudja.wudjabudja_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

// One activity option = one row in itinerary_options (MVP-07).
// Gemini's tree (day -> block -> subgroup -> option) is flattened, so every row repeats
// its date, time_block and subgroup info. ItineraryStore rebuilds the tree when reading.
@Entity
@Table(name = "itinerary_options")
public class ItineraryOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                     // the option id that votes will point at in Sprint 2

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "time_block")
    private String timeBlock;            // "morning", "afternoon" or "evening"

    // JSONB array of user_ids, stored the same way UserPreference stores interests
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subgroup_participants")
    private List<Long> subgroupParticipants = new ArrayList<>();

    @Column(name = "split_reason")
    private String splitReason;          // null when the whole group stays together

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "neighborhood")
    private String neighborhood;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "estimated_cost_usd")
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;   // per person

    @Column(name = "why_it_fits")
    private String whyItFits;

    @Column(name = "is_final")
    private boolean isFinal = false;     // set by the vote tally in Sprint 2

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getTimeBlock() { return timeBlock; }
    public void setTimeBlock(String timeBlock) { this.timeBlock = timeBlock; }

    public List<Long> getSubgroupParticipants() { return subgroupParticipants; }
    public void setSubgroupParticipants(List<Long> subgroupParticipants) { this.subgroupParticipants = subgroupParticipants; }

    public String getSplitReason() { return splitReason; }
    public void setSplitReason(String splitReason) { this.splitReason = splitReason; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public BigDecimal getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }

    public String getWhyItFits() { return whyItFits; }
    public void setWhyItFits(String whyItFits) { this.whyItFits = whyItFits; }

    public boolean isFinal() { return isFinal; }
    public void setFinal(boolean isFinal) { this.isFinal = isFinal; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
