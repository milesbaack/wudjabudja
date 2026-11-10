package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.TripRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Checks the trip rules from MVP-11 without a database.
class TripServiceTest {

    private static TripRequest trip(String start, String end, String type, String budget) {
        return new TripRequest(null, "San Diego", start, end, type, budget == null ? null : new BigDecimal(budget));
    }

    @Test
    void goodTripHasNoProblems() {
        assertEquals(List.of(), TripService.problems(trip("2027-03-12", "2027-03-14", "friends_trip", "2400")));
    }

    @Test
    void endBeforeStartIsRejected() {
        List<String> problems = TripService.problems(trip("2027-03-14", "2027-03-12", "friends_trip", "2400"));
        assertEquals(List.of("end_date can't be before start_date"), problems);
    }

    @Test
    void sevenDaysIsFineButEightIsTooLong() {
        assertEquals(List.of(), TripService.problems(trip("2027-03-01", "2027-03-07", "solo_trip", "500")));
        assertEquals(List.of("trip is 8 days, max is 7"), TripService.problems(trip("2027-03-01", "2027-03-08", "solo_trip", "500")));
    }

    @Test
    void oneDayTripIsFine() {
        assertEquals(List.of(), TripService.problems(trip("2027-03-01", "2027-03-01", "couples_trip", "300")));
    }

    @Test
    void unknownTripTypeIsRejected() {
        List<String> problems = TripService.problems(trip("2027-03-12", "2027-03-14", "business_trip", "2400"));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("trip_type must be one of"), problems.get(0));
    }

    @Test
    void budgetMustBePositive() {
        assertEquals(List.of("total_budget must be more than 0"), TripService.problems(trip("2027-03-12", "2027-03-14", "guys_trip", "0")));
        assertEquals(List.of("total_budget is required"), TripService.problems(trip("2027-03-12", "2027-03-14", "guys_trip", null)));
    }

    @Test
    void missingFieldsAreAllListed() {
        List<String> problems = TripService.problems(new TripRequest(null, " ", null, "03/14/2027", null, null));
        assertEquals(List.of(
                "destination is required",
                "start_date is required",
                "end_date must look like 2027-03-12",
                "trip_type is required",
                "total_budget is required"), problems);
    }

    @Test
    void defaultTitleReadsNaturally() {
        assertEquals("San Diego, Mar 12 to 14",
                TripService.defaultTitle("San Diego", LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 14)));
        assertEquals("Las Vegas, Mar 30 to Apr 1",
                TripService.defaultTitle("Las Vegas", LocalDate.of(2027, 3, 30), LocalDate.of(2027, 4, 1)));
        assertEquals("Austin, May 2",
                TripService.defaultTitle("Austin", LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 2)));
        assertTrue(TripService.defaultTitle("x".repeat(255), LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 3)).length() <= 150);
    }
}
