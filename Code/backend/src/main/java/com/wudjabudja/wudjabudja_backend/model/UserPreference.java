package com.wudjabudja.wudjabudja_backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "wake_time")
    private LocalTime wakeTime = LocalTime.of(8, 0);

    @Column(name = "experience_level")
    private String experienceLevel = "intermediate";

    @Column(name = "max_radius_miles")
    private Integer maxRadiusMiles = 10;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categorical_budgets")
    private Map<String, Double> categoricalBudgets = new HashMap<>();

    @Column(name = "per_block_cap_usd")
    private BigDecimal perBlockCapUsd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interests")
    private List<String> interests = new ArrayList<>();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalTime getWakeTime() { return wakeTime; }
    public void setWakeTime(LocalTime wakeTime) { this.wakeTime = wakeTime; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public Integer getMaxRadiusMiles() { return maxRadiusMiles; }
    public void setMaxRadiusMiles(Integer maxRadiusMiles) { this.maxRadiusMiles = maxRadiusMiles; }

    public Map<String, Double> getCategoricalBudgets() { return categoricalBudgets; }
    public void setCategoricalBudgets(Map<String, Double> categoricalBudgets) { this.categoricalBudgets = categoricalBudgets; }

    public BigDecimal getPerBlockCapUsd() { return perBlockCapUsd; }
    public void setPerBlockCapUsd(BigDecimal perBlockCapUsd) { this.perBlockCapUsd = perBlockCapUsd; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}