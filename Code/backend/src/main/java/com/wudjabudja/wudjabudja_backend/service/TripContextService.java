package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Builds the trip context Gemini needs from what's saved in Postgres (MVP-04).
@Service                         // "Spring, make one of these and hand it to whoever asks"
public class TripContextService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    // Spring hands us both of these automatically
    public TripContextService(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public TripContext build(Long tripId) {

        // Step 1: load the trip. There's no Trip entity yet (Lukas's area), so plain SQL for now.
        List<TripRow> trips = jdbc.query(
                "SELECT creator_id, destination, start_date, end_date, trip_type, total_budget FROM trips WHERE id = ?",
                (rs, rowNum) -> new TripRow(
                        rs.getLong("creator_id"),
                        rs.getString("destination"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getString("trip_type"),
                        rs.getBigDecimal("total_budget")),
                tripId);
        if (trips.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found");
        }
        TripRow trip = trips.get(0);

        // Step 2: load everyone's saved quiz answers, using Lukas's existing UserPreference entity.
        // Swap this for userPreferenceRepository.findByTripId(tripId) once that method exists.
        List<UserPreference> prefs = entityManager
                .createQuery("SELECT p FROM UserPreference p WHERE p.tripId = :tripId ORDER BY p.userId", UserPreference.class)
                .setParameter("tripId", tripId)
                .getResultList();
        if (prefs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No preferences saved for trip " + tripId);
        }

        // Step 3: turn each saved row into a participant
        List<TripContext.Participant> participants = prefs.stream()
                .map(p -> new TripContext.Participant(
                        p.getUserId(),
                        // Real roles come from trip_participants in GRP-01. For now the trip creator leads.
                        p.getUserId().equals(trip.creatorId()) ? "leader" : "member",
                        p.getWakeTime().format(HH_MM),
                        p.getExperienceLevel(),
                        p.getMaxRadiusMiles(),
                        p.getCategoricalBudgets(),
                        p.getPerBlockCapUsd(),
                        p.getInterests()))
                .toList();

        // Step 4: package it up. Mar 12 to Mar 14 counts as 3 days, so add 1.
        int numDays = (int) ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1;
        return new TripContext(
                tripId,
                trip.destination(),
                trip.startDate().toString(),
                trip.endDate().toString(),
                numDays,
                trip.tripType(),
                trip.totalBudget(),
                participants);
    }

    // One row from the trips table, only the columns we need
    private record TripRow(Long creatorId, String destination, LocalDate startDate, LocalDate endDate,
                           String tripType, BigDecimal totalBudget) {}
}
