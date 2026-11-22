package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripParticipant;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Checks group trip context building with 2+ travelers and conflicting preferences (GRP-01).
class TripContextServiceTest {

    @Test
    void buildsGroupContextWithLeaderAndMemberRoles() {
        Trip trip = sampleTrip();
        List<TripParticipant> roster = List.of(
                new TripParticipant(1L, 1L, "leader"),
                new TripParticipant(1L, 2L, "member")
        );
        List<UserPreference> prefs = List.of(
                pref(1L, 1L, "07:30", "intermediate", 15, "30", List.of("sports", "outdoors")),
                pref(1L, 2L, "09:30", "novice", 8, "20", List.of("food", "relaxation"))
        );

        TripContext context = TripContextService.buildContext(
                trip, roster, prefs, Map.of(1L, "demo@wudjabudja.local", 2L, "alex@example.com"));

        assertEquals(1L, context.tripId());
        assertEquals("San Diego", context.destination());
        assertEquals(3, context.numDays());
        assertEquals(2, context.participants().size());

        TripContext.Participant p1 = context.participants().get(0);
        assertEquals(1L, p1.userId());
        assertEquals("leader", p1.role());
        assertEquals("07:30", p1.wakeTime());
        assertEquals(List.of("sports", "outdoors"), p1.interests());

        TripContext.Participant p2 = context.participants().get(1);
        assertEquals(2L, p2.userId());
        assertEquals("member", p2.role());
        assertEquals("09:30", p2.wakeTime());
        assertEquals(List.of("food", "relaxation"), p2.interests());
    }

    @Test
    void rejectsWhenAddedTravelerHasNotSavedQuizYet() {
        Trip trip = sampleTrip();
        List<TripParticipant> roster = List.of(
                new TripParticipant(1L, 1L, "leader"),
                new TripParticipant(1L, 2L, "member")
        );
        List<UserPreference> onlyLeaderPref = List.of(
                pref(1L, 1L, "07:30", "intermediate", 15, "30", List.of("sports"))
        );

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                TripContextService.buildContext(
                        trip, roster, onlyLeaderPref, Map.of(1L, "demo@wudjabudja.local", 2L, "alex@example.com")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("alex@example.com"), error.getReason());
    }

    @Test
    void rejectsWhenNoPreferencesAreSaved() {
        Trip trip = sampleTrip();
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                TripContextService.buildContext(trip, List.of(), List.of(), Map.of()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("No preferences saved for trip 1", error.getReason());
    }

    private static Trip sampleTrip() {
        Trip t = new Trip();
        t.setId(1L);
        t.setCreatorId(1L);
        t.setDestination("San Diego");
        t.setStartDate(LocalDate.of(2027, 3, 12));
        t.setEndDate(LocalDate.of(2027, 3, 14));
        t.setTripType("friends_trip");
        t.setTotalBudget(new BigDecimal("2400"));
        return t;
    }

    private static UserPreference pref(Long tripId, Long userId, String wake, String exp, int radius, String cap, List<String> interests) {
        UserPreference p = new UserPreference();
        p.setTripId(tripId);
        p.setUserId(userId);
        p.setWakeTime(LocalTime.parse(wake));
        p.setExperienceLevel(exp);
        p.setMaxRadiusMiles(radius);
        p.setCategoricalBudgets(Map.of("lodging", 300.0, "transit", 60.0, "food", 150.0, "activities", 120.0));
        p.setPerBlockCapUsd(new BigDecimal(cap));
        p.setInterests(interests);
        return p;
    }
}
