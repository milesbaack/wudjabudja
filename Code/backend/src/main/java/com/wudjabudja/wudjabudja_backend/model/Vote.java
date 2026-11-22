package com.wudjabudja.wudjabudja_backend.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

// One row in the votes table: a traveler's ranked choice (1st=3pts, 2nd=2pts, 3rd=1pt) for an option (VOTE-03).
@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Vote() {}

    public Vote(Long optionId, Long userId, Integer rank, Integer points) {
        this.optionId = optionId;
        this.userId = userId;
        this.rank = rank;
        this.points = points;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
