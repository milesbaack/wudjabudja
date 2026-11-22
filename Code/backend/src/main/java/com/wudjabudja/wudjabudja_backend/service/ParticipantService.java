package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.AddParticipantRequest;
import com.wudjabudja.wudjabudja_backend.dto.ParticipantResponse;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOption;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOptionRepository;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripParticipant;
import com.wudjabudja.wudjabudja_backend.model.TripParticipantRepository;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import com.wudjabudja.wudjabudja_backend.model.User;
import com.wudjabudja.wudjabudja_backend.model.UserPreference;
import com.wudjabudja.wudjabudja_backend.model.UserPreferenceRepository;
import com.wudjabudja.wudjabudja_backend.model.UserRepository;
import com.wudjabudja.wudjabudja_backend.model.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Adds, lists, and removes travelers on a shared trip (GRP-02).
@Service
public class ParticipantService {

    private static final int MAX_EMAIL = 255;
    private static final int MAX_FULL_NAME = 100;
    private static final String PLACEHOLDER_PASSWORD_HASH = "not-a-real-hash";   // same placeholder as seed.sql

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final UserRepository users;
    private final UserPreferenceRepository preferences;
    private final ItineraryOptionRepository options;
    private final VoteRepository votes;

    public ParticipantService(TripRepository trips,
                              TripParticipantRepository participants,
                              UserRepository users,
                              UserPreferenceRepository preferences) {
        this(trips, participants, users, preferences, null, null);
    }

    @Autowired
    public ParticipantService(TripRepository trips,
                              TripParticipantRepository participants,
                              UserRepository users,
                              UserPreferenceRepository preferences,
                              ItineraryOptionRepository options,
                              VoteRepository votes) {
        this.trips = trips;
        this.participants = participants;
        this.users = users;
        this.preferences = preferences;
        this.options = options;
        this.votes = votes;
    }

    // Lists every traveler on the trip with their role and whether their preferences quiz is saved.
    @Transactional(readOnly = true)
    public List<ParticipantResponse> listForTrip(Long tripId) {
        Trip trip = requireTrip(tripId);
        List<TripParticipant> rows = new ArrayList<>(participants.findByTripIdOrderByIdAsc(tripId));

        // Safety net if a trip was inserted directly into SQL without a trip_participants row for its creator
        if (rows.isEmpty() && trip.getCreatorId() != null) {
            rows.add(new TripParticipant(tripId, trip.getCreatorId(), "leader"));
        }

        Set<Long> savedQuizUserIds = new HashSet<>();
        for (UserPreference pref : preferences.findByTripIdOrderByUserIdAsc(tripId)) {
            if (pref.getUserId() != null) {
                savedQuizUserIds.add(pref.getUserId());
            }
        }

        List<Long> userIds = rows.stream()
                .map(TripParticipant::getUserId)
                .filter(id -> id != null)
                .toList();
        Map<Long, User> usersById = new HashMap<>();
        for (User u : users.findAllById(userIds)) {
            usersById.put(u.getId(), u);
        }

        List<ParticipantResponse> result = new ArrayList<>();
        for (TripParticipant row : rows) {
            User u = usersById.get(row.getUserId());
            String email = u != null ? u.getEmail() : "traveler" + row.getUserId() + "@wudjabudja.local";
            String fullName = u != null ? u.getFullName() : "Traveler " + row.getUserId();
            String role = row.getRole() != null ? row.getRole() : "member";
            boolean quizSaved = savedQuizUserIds.contains(row.getUserId());
            result.add(new ParticipantResponse(row.getUserId(), email, fullName, role, quizSaved));
        }
        return result;
    }

    // Adds a traveler by email as a 'member', creating the users row first if the email is new.
    @Transactional
    public ParticipantResponse addByEmail(Long tripId, AddParticipantRequest request) {
        List<String> problems = problems(request);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }

        requireTrip(tripId);

        String cleanEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String requestedName = request.fullName() == null || request.fullName().isBlank()
                ? deriveNameFromEmail(cleanEmail)
                : cut(request.fullName().trim(), MAX_FULL_NAME);

        User user = users.findByEmailIgnoreCase(cleanEmail)
                .orElseGet(() -> users.save(new User(cleanEmail, PLACEHOLDER_PASSWORD_HASH, requestedName)));

        if (participants.existsByTripIdAndUserId(tripId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Traveler " + user.getEmail() + " is already on this trip");
        }

        TripParticipant saved = participants.save(new TripParticipant(tripId, user.getId(), "member"));
        boolean quizSaved = preferences.existsByTripIdAndUserId(tripId, user.getId());
        return new ParticipantResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                saved.getRole(),
                quizSaved
        );
    }

    // Removes a member traveler from the trip, along with their saved quiz preferences and votes for that trip.
    @Transactional
    public void removeParticipant(Long tripId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_id is required");
        }
        Trip trip = requireTrip(tripId);
        TripParticipant row = participants.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Traveler " + userId + " is not on trip " + tripId));

        if ("leader".equalsIgnoreCase(row.getRole()) || userId.equals(trip.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The trip leader cannot be removed from the trip");
        }

        participants.delete(row);
        preferences.findByTripIdAndUserId(tripId, userId).ifPresent(preferences::delete);

        if (options != null && votes != null) {
            List<Long> optionIds = options.findByTripIdOrderByIdAsc(tripId).stream()
                    .map(ItineraryOption::getId)
                    .filter(id -> id != null)
                    .toList();
            if (!optionIds.isEmpty()) {
                votes.deleteByUserIdAndOptionIdIn(userId, optionIds);
            }
        }
    }

    // Pure validation helper so ParticipantServiceTest can check bad requests without a database.
    static List<String> problems(AddParticipantRequest request) {
        List<String> problems = new ArrayList<>();
        if (request == null || request.email() == null || request.email().isBlank()) {
            problems.add("email is required");
            return problems;
        }
        String email = request.email().trim();
        if (email.length() > MAX_EMAIL) {
            problems.add("email is too long (max " + MAX_EMAIL + " characters)");
        }
        int at = email.indexOf('@');
        int lastAt = email.lastIndexOf('@');
        if (at <= 0 || at != lastAt || at == email.length() - 1) {
            problems.add("email must be a valid email address");
        } else {
            String domain = email.substring(at + 1);
            if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
                problems.add("email must be a valid email address");
            }
        }
        if (request.fullName() != null && request.fullName().trim().length() > MAX_FULL_NAME) {
            problems.add("full_name is too long (max " + MAX_FULL_NAME + " characters)");
        }
        return problems;
    }

    // "alex.smith@example.com" -> "Alex smith"
    static String deriveNameFromEmail(String email) {
        if (email == null) return "Traveler";
        int at = email.indexOf('@');
        String prefix = (at > 0 ? email.substring(0, at) : email)
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (prefix.isEmpty()) return "Traveler";
        String capitalized = prefix.substring(0, 1).toUpperCase(Locale.ROOT) + prefix.substring(1);
        return cut(capitalized, MAX_FULL_NAME);
    }

    private Trip requireTrip(Long tripId) {
        return trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"));
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
