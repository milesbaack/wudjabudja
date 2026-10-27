package com.wudjabudja.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Data transfer model capturing granular traveler constraints for PREF-02.
 * Maps directly to the PostgreSQL user_preferences schema and backend DTO.
 */
public class PreferenceRequest {

    @JsonProperty("trip_id")
    private Long tripId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("wake_time")
    private String wakeTime; // format: "HH:mm" (e.g. "08:30")

    @JsonProperty("experience_level")
    private String experienceLevel; // "novice", "intermediate", "expert"

    @JsonProperty("max_radius_miles")
    private Integer maxRadiusMiles;

    @JsonProperty("categorical_budgets")
    private Map<String, Double> categoricalBudgets; // lodging, transit, food, activities

    @JsonProperty("per_block_cap_usd")
    private BigDecimal perBlockCapUsd;

    @JsonProperty("interests")
    private List<String> interests;

    public PreferenceRequest() {}

    public PreferenceRequest(Long tripId, Long userId, String wakeTime, String experienceLevel,
                             Integer maxRadiusMiles, Map<String, Double> categoricalBudgets,
                             BigDecimal perBlockCapUsd, List<String> interests) {
        this.tripId = tripId;
        this.userId = userId;
        this.wakeTime = wakeTime;
        this.experienceLevel = experienceLevel;
        this.maxRadiusMiles = maxRadiusMiles;
        this.categoricalBudgets = categoricalBudgets;
        this.perBlockCapUsd = perBlockCapUsd;
        this.interests = interests;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getWakeTime() {
        return wakeTime;
    }

    public void setWakeTime(String wakeTime) {
        this.wakeTime = wakeTime;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public Integer getMaxRadiusMiles() {
        return maxRadiusMiles;
    }

    public void setMaxRadiusMiles(Integer maxRadiusMiles) {
        this.maxRadiusMiles = maxRadiusMiles;
    }

    public Map<String, Double> getCategoricalBudgets() {
        return categoricalBudgets;
    }

    public void setCategoricalBudgets(Map<String, Double> categoricalBudgets) {
        this.categoricalBudgets = categoricalBudgets;
    }

    public BigDecimal getPerBlockCapUsd() {
        return perBlockCapUsd;
    }

    public void setPerBlockCapUsd(BigDecimal perBlockCapUsd) {
        this.perBlockCapUsd = perBlockCapUsd;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    @Override
    public String toString() {
        return "PreferenceRequest{" +
                "tripId=" + tripId +
                ", userId=" + userId +
                ", wakeTime='" + wakeTime + '\'' +
                ", experienceLevel='" + experienceLevel + '\'' +
                ", maxRadiusMiles=" + maxRadiusMiles +
                ", categoricalBudgets=" + categoricalBudgets +
                ", perBlockCapUsd=" + perBlockCapUsd +
                ", interests=" + interests +
                '}';
    }
}
