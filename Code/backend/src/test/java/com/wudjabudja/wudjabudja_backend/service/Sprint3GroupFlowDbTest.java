package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.controller.PreferenceController;
import com.wudjabudja.wudjabudja_backend.dto.AddParticipantRequest;
import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.ItineraryResponse;
import com.wudjabudja.wudjabudja_backend.dto.ParticipantResponse;
import com.wudjabudja.wudjabudja_backend.dto.PreferenceRequest;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.dto.TripRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest.RankingItem;
import com.wudjabudja.wudjabudja_backend.dto.VoteWinnerResponse;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// End-to-end database test for the Sprint 3 group trip & Borda voting flow against local Postgres.
// @Transactional rolls back at the end so no test rows are left behind.
@SpringBootTest
@Transactional
class Sprint3GroupFlowDbTest {

    @Autowired TripService tripService;
    @Autowired ParticipantService participantService;
    @Autowired PreferenceController preferenceController;
    @Autowired TripContextService tripContextService;
    @Autowired ItineraryStore itineraryStore;
    @Autowired VoteService voteService;
    @Autowired EntityManager entityManager;

    @Test
    void groupTripParticipantsPreferencesAndBordaVotesPersistAndTallyWinners() {
        // 1. Create a 1-day group trip (user 1 becomes leader)
        Trip trip = tripService.create(new TripRequest(
                "San Diego Group Getaway", "San Diego", "2027-03-12", "2027-03-12", "friends_trip", new BigDecimal("1800")));
        Long tripId = trip.getId();

        // 2. Add a second traveler by email (GRP-02)
        ParticipantResponse alex = participantService.addByEmail(tripId, new AddParticipantRequest("alex.sprint3@example.com", "Alex"));
        assertNotNull(alex.userId());
        assertEquals("member", alex.role());
        assertFalse(alex.quizSaved());

        // Adding the same email twice is rejected
        ResponseStatusException duplicateErr = assertThrows(ResponseStatusException.class,
                () -> participantService.addByEmail(tripId, new AddParticipantRequest("ALEX.SPRINT3@example.com")));
        assertEquals(HttpStatus.CONFLICT, duplicateErr.getStatusCode());

        // 3. Save preferences for both user 1 and Alex
        preferenceController.savePreferences(tripId, new PreferenceRequest(
                tripId, TripService.DEMO_USER_ID, "07:30", "intermediate", 15,
                Map.of("lodging", 300.0, "transit", 60.0, "food", 150.0, "activities", 120.0),
                new BigDecimal("35"), List.of("sports", "outdoors")));
        preferenceController.savePreferences(tripId, new PreferenceRequest(
                tripId, alex.userId(), "09:00", "novice", 10,
                Map.of("lodging", 250.0, "transit", 50.0, "food", 140.0, "activities", 90.0),
                new BigDecimal("25"), List.of("food", "relaxation")));

        List<ParticipantResponse> roster = participantService.listForTrip(tripId);
        assertEquals(2, roster.size());
        assertTrue(roster.stream().allMatch(ParticipantResponse::quizSaved), "both travelers should now show quiz_saved = true");

        // 4. Build group trip context (GRP-01)
        TripContext context = tripContextService.build(tripId);
        assertEquals(2, context.participants().size());

        // 5. Save a 1-day morning itinerary with 3 options
        List<Long> both = List.of(TripService.DEMO_USER_ID, alex.userId());
        Itinerary sample = new Itinerary(List.of(
                new Itinerary.Day("2027-03-12", List.of(
                        new Itinerary.Block("morning", List.of(
                                new Itinerary.Subgroup(both, null, List.of(
                                        new Itinerary.Option("Kayak Caves", "Kayaking", "outdoors", "La Jolla", "10:00", "12:00", new BigDecimal("40.00"), "Outdoors."),
                                        new Itinerary.Option("Balboa Gardens", "Stroll", "outdoors", "Balboa Park", "10:00", "12:00", new BigDecimal("15.00"), "Relaxing."),
                                        new Itinerary.Option("Harbor Cruise", "Boat tour", "tour", "Embarcadero", "10:00", "12:00", new BigDecimal("25.00"), "Sightseeing.")
                                ))
                        ))
                ))
        ));
        ItineraryResponse savedItinerary = itineraryStore.replaceForTrip(tripId, sample);
        List<ItineraryResponse.Option> savedOpts = savedItinerary.days().get(0).blocks().get(0).subgroups().get(0).options();
        Long opt1Id = savedOpts.get(0).id(); // $40
        Long opt2Id = savedOpts.get(1).id(); // $15
        Long opt3Id = savedOpts.get(2).id(); // $25

        // 6. Both travelers submit Borda votes (VOTE-02 / VOTE-03 / VOTE-04)
        // User 1 ranks: 1st = opt1 ($40, 3pts), 2nd = opt2 ($15, 2pts), 3rd = opt3 ($25, 1pt)
        // Alex ranks:   1st = opt2 ($15, 3pts), 2nd = opt1 ($40, 2pts), 3rd = opt3 ($25, 1pt)
        // Tie at 5 pts between opt1 ($40) and opt2 ($15) -> opt2 wins by lowest cost ($15)!
        voteService.submitVotes(tripId, new VoteSubmissionRequest(TripService.DEMO_USER_ID, List.of(
                new RankingItem(opt1Id, 1),
                new RankingItem(opt2Id, 2),
                new RankingItem(opt3Id, 3)
        )));
        List<VoteWinnerResponse> winners = voteService.submitVotes(tripId, new VoteSubmissionRequest(alex.userId(), List.of(
                new RankingItem(opt2Id, 1),
                new RankingItem(opt1Id, 2),
                new RankingItem(opt3Id, 3)
        )));

        entityManager.flush();
        entityManager.clear();

        assertEquals(1, winners.size());
        assertEquals(opt2Id, winners.get(0).optionId());
        assertEquals(5, winners.get(0).totalPoints());
        assertEquals(2, winners.get(0).voteCount());

        // Reload itinerary from Postgres and check is_final = true on the winner
        ItineraryResponse reloaded = itineraryStore.loadForTrip(tripId).orElseThrow();
        List<ItineraryResponse.Option> reloadedOpts = reloaded.days().get(0).blocks().get(0).subgroups().get(0).options();
        assertFalse(reloadedOpts.get(0).isFinal());
        assertTrue(reloadedOpts.get(1).isFinal(), "winning option should have is_final = true in Postgres");
        assertFalse(reloadedOpts.get(2).isFinal());

        // 7. Removing the trip leader is blocked; removing a member cleans up roster, preferences, and votes
        ResponseStatusException removeLeaderErr = assertThrows(ResponseStatusException.class,
                () -> participantService.removeParticipant(tripId, TripService.DEMO_USER_ID));
        assertEquals(HttpStatus.BAD_REQUEST, removeLeaderErr.getStatusCode());

        participantService.removeParticipant(tripId, alex.userId());
        entityManager.flush();
        entityManager.clear();

        List<ParticipantResponse> remaining = participantService.listForTrip(tripId);
        assertEquals(1, remaining.size());
        assertEquals(TripService.DEMO_USER_ID, remaining.get(0).userId());
        assertEquals(1, tripContextService.build(tripId).participants().size());
    }
}
