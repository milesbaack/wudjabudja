package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripParticipant;
import com.wudjabudja.wudjabudja_backend.model.TripParticipantRepository;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import com.wudjabudja.wudjabudja_backend.model.User;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import com.wudjabudja.wudjabudja_backend.model.UserPreferenceRepository;
import com.wudjabudja.wudjabudja_backend.model.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Builds the trip context Gemini needs from trips, trip_participants and user_preferences (MVP-04 / GRP-01).
@Service
public class TripContextService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final UserPreferenceRepository preferences;
    private final UserRepository users;

    public TripContextService(TripRepository trips,
                              TripParticipantRepository participants,
                              UserPreferenceRepository preferences,
                              UserRepository users) {
        this.trips = trips;
        this.participants = participants;
        this.preferences = preferences;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public TripContext build(Long tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"));

        List<TripParticipant> roster = participants.findByTripIdOrderByIdAsc(tripId);
        List<UserPreference> prefs = preferences.findByTripIdOrderByUserIdAsc(tripId);

        Map<Long, String> labelsByUserId = new HashMap<>();
        List<Long> rosterUserIds = roster.stream()
                .map(TripParticipant::getUserId)
                .filter(id -> id != null)
                .toList();
        if (!rosterUserIds.isEmpty()) {
            for (User u : users.findAllById(rosterUserIds)) {
                labelsByUserId.put(u.getId(), u.getEmail() != null ? u.getEmail() : "Traveler " + u.getId());
            }
        }

        return buildContext(trip, roster, prefs, labelsByUserId);
    }

    // Builds trip context for the Trip Briefing engine (BRIEF-01). Uses saved quiz answers when present,
    // and fills in sensible defaults for travelers who haven't taken the quiz yet so briefing generation never blocks.
    @Transactional(readOnly = true)
    public TripContext buildForBriefing(Long tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"));

        List<TripParticipant> roster = new ArrayList<>(participants.findByTripIdOrderByIdAsc(tripId));
        if (roster.isEmpty() && trip.getCreatorId() != null) {
            roster.add(new TripParticipant(tripId, trip.getCreatorId(), "leader"));
        }

        List<UserPreference> prefs = preferences.findByTripIdOrderByUserIdAsc(tripId);
        Map<Long, UserPreference> prefByUserId = new LinkedHashMap<>();
        for (UserPreference p : prefs) {
            if (p.getUserId() != null) {
                prefByUserId.put(p.getUserId(), p);
            }
        }

        List<TripContext.Participant> contextParticipants = new ArrayList<>();
        for (TripParticipant tp : roster) {
            Long userId = tp.getUserId();
            if (userId == null) continue;
            String role = (tp.getRole() == null || tp.getRole().isBlank()) ? "member" : tp.getRole();
            UserPreference p = prefByUserId.get(userId);
            if (p != null) {
                LocalTime wake = p.getWakeTime() != null ? p.getWakeTime() : LocalTime.of(8, 0);
                contextParticipants.add(new TripContext.Participant(
                        userId, role, wake.format(HH_MM), p.getExperienceLevel(), p.getMaxRadiusMiles(),
                        p.getCategoricalBudgets(), p.getPerBlockCapUsd(), p.getInterests()
                ));
            } else {
                contextParticipants.add(new TripContext.Participant(
                        userId, role, "08:30", "intermediate", 15,
                        Map.of("lodging", 300.0, "transit", 60.0, "food", 150.0, "activities", 120.0),
                        new java.math.BigDecimal("40"), List.of("food", "culture", "outdoors")
                ));
            }
        }

        if (contextParticipants.isEmpty()) {
            contextParticipants.add(new TripContext.Participant(
                    TripService.DEMO_USER_ID, "leader", "08:30", "intermediate", 15,
                    Map.of("lodging", 300.0, "transit", 60.0, "food", 150.0, "activities", 120.0),
                    new java.math.BigDecimal("40"), List.of("food", "culture", "outdoors")
            ));
        }

        int numDays = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return new TripContext(
                tripId,
                trip.getDestination(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                numDays,
                trip.getTripType(),
                trip.getTotalBudget(),
                contextParticipants
        );
    }

    // Pure aggregation helper so TripContextServiceTest can verify multi-traveler context building without a database.
    static TripContext buildContext(
            Trip trip,
            List<TripParticipant> roster,
            List<UserPreference> prefs,
            Map<Long, String> userLabels) {

        Long tripId = trip.getId();
        if (prefs == null || prefs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No preferences saved for trip " + tripId);
        }

        Map<Long, String> roleByUserId = new LinkedHashMap<>();
        if (roster != null) {
            for (TripParticipant tp : roster) {
                if (tp.getUserId() != null) {
                    String role = (tp.getRole() == null || tp.getRole().isBlank()) ? "member" : tp.getRole();
                    roleByUserId.put(tp.getUserId(), role);
                }
            }
        }

        Map<Long, UserPreference> prefByUserId = new LinkedHashMap<>();
        for (UserPreference p : prefs) {
            if (p.getUserId() != null) {
                prefByUserId.put(p.getUserId(), p);
            }
        }

        // Every traveler added to trip_participants must have saved their quiz before Gemini plans for the group
        List<String> missingTravelers = new ArrayList<>();
        for (Long userId : roleByUserId.keySet()) {
            if (!prefByUserId.containsKey(userId)) {
                String label = userLabels != null && userLabels.containsKey(userId)
                        ? userLabels.get(userId)
                        : "Traveler " + userId;
                missingTravelers.add(label);
            }
        }
        if (!missingTravelers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Every traveler on this trip must save their preferences quiz before generating (missing: "
                            + String.join(", ", missingTravelers) + ")");
        }

        List<TripContext.Participant> contextParticipants = new ArrayList<>();
        for (UserPreference p : prefs) {
            String role = roleByUserId.getOrDefault(
                    p.getUserId(),
                    p.getUserId() != null && p.getUserId().equals(trip.getCreatorId()) ? "leader" : "member"
            );
            LocalTime wake = p.getWakeTime() != null ? p.getWakeTime() : LocalTime.of(8, 0);
            contextParticipants.add(new TripContext.Participant(
                    p.getUserId(),
                    role,
                    wake.format(HH_MM),
                    p.getExperienceLevel(),
                    p.getMaxRadiusMiles(),
                    p.getCategoricalBudgets(),
                    p.getPerBlockCapUsd(),
                    p.getInterests()
            ));
        }

        int numDays = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return new TripContext(
                tripId,
                trip.getDestination(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                numDays,
                trip.getTripType(),
                trip.getTotalBudget(),
                contextParticipants
        );
    }
}
