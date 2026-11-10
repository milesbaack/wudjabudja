package com.wudjabudja.wudjabudja_backend.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

// One row in trip_participants: "this user is on this trip as leader or member".
@Entity
@Table(name = "trip_participants")
public class TripParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role")
    private String role = "member";   // "leader" or "member"

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    public TripParticipant() {}

    public TripParticipant(Long tripId, Long userId, String role) {
        this.tripId = tripId;
        this.userId = userId;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
}
