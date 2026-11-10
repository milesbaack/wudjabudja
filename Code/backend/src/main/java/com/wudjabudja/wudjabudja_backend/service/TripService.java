package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.TripRequest;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripParticipant;
import com.wudjabudja.wudjabudja_backend.model.TripParticipantRepository;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Checks and creates trips (TRIP-01). Sprint 1 is single-user, so user 1 creates and leads every trip.
@Service
public class TripService {

    public static final Long DEMO_USER_ID = 1L;      // seeded by Code/database/seed.sql
    public static final int MAX_DAYS = 7;            // longer trips make Gemini slow and more likely to fail checks

    public static final Set<String> TRIP_TYPES = Set.of(
            "guys_trip", "girls_trip", "friends_trip", "couples_trip", "family_trip", "solo_trip");

    // Column limits from schema.sql, so bad input gets a clear 400 instead of a database error
    private static final int MAX_TITLE = 150;
    private static final int MAX_DESTINATION = 255;
    private static final BigDecimal MAX_BUDGET = new BigDecimal("99999999.99");   // NUMERIC(10, 2)

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public TripService(TripRepository trips, TripParticipantRepository participants, JdbcTemplate jdbc) {
        this.trips = trips;
        this.participants = participants;
        this.jdbc = jdbc;
    }

    // Saves the trip and adds user 1 as its leader. @Transactional: both rows save, or neither does.
    @Transactional
    public Trip create(TripRequest request) {
        List<String> problems = problems(request);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }

        // Clearer than a foreign key error if someone skipped the seed step
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, DEMO_USER_ID);
        if (users == null || users == 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Demo user " + DEMO_USER_ID + " is missing. Run Code/database/seed.sql");
        }

        LocalDate start = LocalDate.parse(request.startDate());
        LocalDate end = LocalDate.parse(request.endDate());
        String destination = request.destination().trim();
        String title = request.title() == null || request.title().isBlank()
                ? defaultTitle(destination, start, end)
                : request.title().trim();

        Trip trip = new Trip();
        trip.setCreatorId(DEMO_USER_ID);
        trip.setTitle(title);
        trip.setDestination(destination);
        trip.setStartDate(start);
        trip.setEndDate(end);
        trip.setTotalBudget(request.totalBudget());
        trip.setTripType(request.tripType());
        trip.setJoinCode(newJoinCode());
        Trip saved = trips.save(trip);

        participants.save(new TripParticipant(saved.getId(), DEMO_USER_ID, "leader"));
        return saved;
    }

    public Trip get(Long id) {
        return trips.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + id + " not found"));
    }

    public List<Trip> listForDemoUser() {
        return trips.findByCreatorIdOrderByIdDesc(DEMO_USER_ID);
    }

    // Every rule the request breaks, in plain English. Empty list = good to save.
    // Static and DB-free so TripServiceTest can check it directly.
    static List<String> problems(TripRequest r) {
        List<String> problems = new ArrayList<>();
        if (r == null) return List.of("Request body is required");

        if (r.destination() == null || r.destination().isBlank()) problems.add("destination is required");
        else if (r.destination().trim().length() > MAX_DESTINATION) problems.add("destination is too long (max " + MAX_DESTINATION + " characters)");

        if (r.title() != null && r.title().trim().length() > MAX_TITLE) problems.add("title is too long (max " + MAX_TITLE + " characters)");

        LocalDate start = parseDate(r.startDate(), "start_date", problems);
        LocalDate end = parseDate(r.endDate(), "end_date", problems);
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                problems.add("end_date can't be before start_date");
            } else {
                long days = ChronoUnit.DAYS.between(start, end) + 1;   // Mar 12 to Mar 14 = 3 days
                if (days > MAX_DAYS) problems.add("trip is " + days + " days, max is " + MAX_DAYS);
            }
        }

        if (r.tripType() == null || r.tripType().isBlank()) problems.add("trip_type is required");
        else if (!TRIP_TYPES.contains(r.tripType())) problems.add("trip_type must be one of " + String.join(", ", TRIP_TYPES.stream().sorted().toList()));

        if (r.totalBudget() == null) problems.add("total_budget is required");
        else if (r.totalBudget().signum() <= 0) problems.add("total_budget must be more than 0");
        else if (r.totalBudget().compareTo(MAX_BUDGET) > 0) problems.add("total_budget is too large");

        return problems;
    }

    // "San Diego, Mar 12 to 14", "Las Vegas, Mar 30 to Apr 1", or "Austin, Mar 12" for a one-day trip
    static String defaultTitle(String destination, LocalDate start, LocalDate end) {
        String dates;
        if (start.equals(end)) dates = start.format(MONTH_DAY);
        else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) dates = start.format(MONTH_DAY) + " to " + end.getDayOfMonth();
        else dates = start.format(MONTH_DAY) + " to " + end.format(MONTH_DAY);

        String title = destination + ", " + dates;
        // Long destinations could push past the column limit, so keep the dates and trim the name
        if (title.length() > MAX_TITLE) title = destination.substring(0, MAX_TITLE - dates.length() - 2) + ", " + dates;
        return title;
    }

    private static LocalDate parseDate(String value, String field, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is required");
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            problems.add(field + " must look like 2027-03-12");
            return null;
        }
    }

    // WUDJA- plus 6 random letters/digits. join_code is UNIQUE, so retry on the rare collision.
    private String newJoinCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder("WUDJA-");
            for (int i = 0; i < 6; i++) code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            if (!trips.existsByJoinCode(code.toString())) return code.toString();
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not make a unique join code");
    }
}
