package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest.RankingItem;
import com.wudjabudja.wudjabudja_backend.dto.VoteWinnerResponse;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOption;
import com.wudjabudja.wudjabudja_backend.model.Vote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Checks vote validation (VOTE-03) and Borda Count tally + lowest-cost tie-breaker (VOTE-04) without a database.
class VoteServiceTest {

    private static final LocalDate DAY_1 = LocalDate.of(2027, 3, 12);

    private static List<ItineraryOption> morningOptions() {
        return List.of(
                option(101L, DAY_1, "morning", List.of(1L, 2L), "Kayak Tour", "45.00"),
                option(102L, DAY_1, "morning", List.of(1L, 2L), "Botanical Walk", "15.00"),
                option(103L, DAY_1, "morning", List.of(1L, 2L), "Surf Lesson", "60.00")
        );
    }

    @Test
    void validVoteBallotPassesChecks() {
        VoteSubmissionRequest request = new VoteSubmissionRequest(1L, List.of(
                new RankingItem(101L, 1),
                new RankingItem(102L, 2),
                new RankingItem(103L, 3)
        ));

        List<String> problems = VoteService.validateSubmission(1L, Set.of(1L, 2L), morningOptions(), request);
        assertEquals(List.of(), problems);
    }

    @Test
    void nonParticipantVoterIsRejected() {
        VoteSubmissionRequest request = new VoteSubmissionRequest(99L, List.of(
                new RankingItem(101L, 1)
        ));

        List<String> problems = VoteService.validateSubmission(1L, Set.of(1L, 2L), morningOptions(), request);
        assertEquals(List.of("User 99 is not a participant on trip 1"), problems);
    }

    @Test
    void rankingSameOptionTwiceIsBlocked() {
        VoteSubmissionRequest request = new VoteSubmissionRequest(1L, List.of(
                new RankingItem(101L, 1),
                new RankingItem(101L, 2)
        ));

        List<String> problems = VoteService.validateSubmission(1L, Set.of(1L, 2L), morningOptions(), request);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Duplicate vote for option 101"), problems.get(0));
    }

    @Test
    void duplicateRankInSameTimeBlockIsBlocked() {
        VoteSubmissionRequest request = new VoteSubmissionRequest(1L, List.of(
                new RankingItem(101L, 1),
                new RankingItem(102L, 1),
                new RankingItem(103L, 2)
        ));

        List<String> problems = VoteService.validateSubmission(1L, Set.of(1L, 2L), morningOptions(), request);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Duplicate rank 1 in 2027-03-12 morning"), problems.get(0));
    }

    @Test
    void bordaCountPicksConsensusWinnerAndSetsIsFinal() {
        List<ItineraryOption> options = morningOptions();
        // Traveler 1: 1st = 101 (3 pts), 2nd = 102 (2 pts), 3rd = 103 (1 pt)
        // Traveler 2: 1st = 103 (3 pts), 2nd = 102 (2 pts), 3rd = 101 (1 pt)
        // Traveler 3: 1st = 102 (3 pts), 2nd = 101 (2 pts), 3rd = 103 (1 pt)
        // Totals: 102 = 7 pts, 101 = 6 pts, 103 = 5 pts -> 102 wins!
        List<Vote> votes = List.of(
                new Vote(101L, 1L, 1, 3), new Vote(102L, 1L, 2, 2), new Vote(103L, 1L, 3, 1),
                new Vote(103L, 2L, 1, 3), new Vote(102L, 2L, 2, 2), new Vote(101L, 2L, 3, 1),
                new Vote(102L, 3L, 1, 3), new Vote(101L, 3L, 2, 2), new Vote(103L, 3L, 3, 1)
        );

        List<VoteWinnerResponse> winners = VoteService.tallyWinners(options, votes);

        assertEquals(1, winners.size());
        VoteWinnerResponse winner = winners.get(0);
        assertEquals(102L, winner.optionId());
        assertEquals("Botanical Walk", winner.title());
        assertEquals(7, winner.totalPoints());
        assertEquals(3, winner.voteCount());

        assertFalse(options.get(0).isFinal(), "101 should not be final");
        assertTrue(options.get(1).isFinal(), "102 should be marked is_final = true");
        assertFalse(options.get(2).isFinal(), "103 should not be final");
    }

    @Test
    void tieOnBordaPointsGoesToLowestEstimatedCost() {
        List<ItineraryOption> options = morningOptions();
        // Traveler 1: 1st = 101 ($45, 3 pts), 2nd = 102 ($15, 2 pts), 3rd = 103 ($60, 1 pt)
        // Traveler 2: 1st = 102 ($15, 3 pts), 2nd = 101 ($45, 2 pts), 3rd = 103 ($60, 1 pt)
        // Both 101 and 102 tie with 5 points! 102 ($15.00) beats 101 ($45.00) on lowest cost.
        List<Vote> votes = List.of(
                new Vote(101L, 1L, 1, 3), new Vote(102L, 1L, 2, 2), new Vote(103L, 1L, 3, 1),
                new Vote(102L, 2L, 1, 3), new Vote(101L, 2L, 2, 2), new Vote(103L, 2L, 3, 1)
        );

        List<VoteWinnerResponse> winners = VoteService.tallyWinners(options, votes);

        assertEquals(1, winners.size());
        assertEquals(102L, winners.get(0).optionId(), "cheaper option ($15 vs $45) should win the tie");
        assertEquals(5, winners.get(0).totalPoints());
        assertTrue(options.get(1).isFinal());
        assertFalse(options.get(0).isFinal());
    }

    @Test
    void splitSubgroupsInSameTimeBlockEachElectTheirOwnWinner() {
        List<ItineraryOption> splitAfternoon = List.of(
                option(201L, DAY_1, "afternoon", List.of(1L), "Padres Game", "30.00"),
                option(202L, DAY_1, "afternoon", List.of(1L), "Sports Bar", "20.00"),
                option(203L, DAY_1, "afternoon", List.of(1L), "Stadium Tour", "25.00"),
                option(204L, DAY_1, "afternoon", List.of(2L), "Spa Afternoon", "25.00"),
                option(205L, DAY_1, "afternoon", List.of(2L), "Beach Reading", "0.00"),
                option(206L, DAY_1, "afternoon", List.of(2L), "Tea Garden", "15.00")
        );

        List<Vote> votes = List.of(
                new Vote(201L, 1L, 1, 3), new Vote(202L, 1L, 2, 2), new Vote(203L, 1L, 3, 1),
                new Vote(205L, 2L, 1, 3), new Vote(206L, 2L, 2, 2), new Vote(204L, 2L, 3, 1)
        );

        List<VoteWinnerResponse> winners = VoteService.tallyWinners(splitAfternoon, votes);

        assertEquals(2, winners.size(), "each subgroup in a split block gets its own winner");
        assertEquals(201L, winners.get(0).optionId());
        assertEquals(205L, winners.get(1).optionId());
    }

    private static ItineraryOption option(Long id, LocalDate date, String block, List<Long> subgroup, String title, String cost) {
        ItineraryOption row = new ItineraryOption();
        row.setId(id);
        row.setTripId(1L);
        row.setDate(date);
        row.setTimeBlock(block);
        row.setSubgroupParticipants(subgroup);
        row.setTitle(title);
        row.setDescription(title + " description");
        row.setCategory("outdoors");
        row.setNeighborhood("Downtown");
        row.setStartTime(LocalTime.of(9, 30));
        row.setEndTime(LocalTime.of(11, 30));
        row.setEstimatedCostUsd(new BigDecimal(cost));
        return row;
    }
}
