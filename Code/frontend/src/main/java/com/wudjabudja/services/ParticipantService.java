package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wudjabudja.models.AddParticipantRequest;
import com.wudjabudja.models.Participant;
import com.wudjabudja.session.AppSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads and adds travelers for a shared trip through Spring Boot (GRP-02 / GRP-03).
 * Routes:
 * - GET  /api/trips/{id}/participants
 * - POST /api/trips/{id}/participants  (body: {"email": "..."})
 *
 * Includes a local in-memory fallback when George's GRP-02 routes return 404 or the backend
 * is offline, so GRP-03 and VOTE-01 can be tested end-to-end immediately.
 */
public class ParticipantService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Local fallback store per tripId when backend GRP-02 routes are not running yet
    private static final Map<Long, List<Participant>> LOCAL_PARTICIPANTS = new HashMap<>();
    private static final Map<Long, Set<Long>> LOCAL_QUIZ_SAVED_USERS = new HashMap<>();
    private static final AtomicLong NEXT_LOCAL_USER_ID = new AtomicLong(2L);

    private final BackendClient client = new BackendClient();

    public static void markQuizSavedLocally(Long tripId, Long userId) {
        if (tripId == null || userId == null) return;
        LOCAL_QUIZ_SAVED_USERS.computeIfAbsent(tripId, k -> new HashSet<>()).add(userId);
        List<Participant> list = LOCAL_PARTICIPANTS.get(tripId);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Participant p = list.get(i);
                if (userId.equals(p.userId()) && !p.quizSaved()) {
                    list.set(i, new Participant(p.userId(), p.email(), p.fullName(), p.role(), true));
                }
            }
        }
    }

    public static boolean isQuizSavedLocally(Long tripId, Long userId) {
        if (tripId == null || userId == null) return false;
        return LOCAL_QUIZ_SAVED_USERS.getOrDefault(tripId, Set.of()).contains(userId);
    }

    private static List<Participant> ensureLocalRoster(Long tripId) {
        return LOCAL_PARTICIPANTS.computeIfAbsent(tripId, id -> {
            List<Participant> initial = new ArrayList<>();
            boolean leaderSaved = isQuizSavedLocally(id, AppSession.USER_ID);
            initial.add(new Participant(
                    AppSession.USER_ID,
                    AppSession.DEFAULT_EMAIL,
                    AppSession.DEFAULT_NAME,
                    "leader",
                    leaderSaved
            ));
            return initial;
        });
    }

    /**
     * GET /api/trips/{tripId}/participants -> list of travelers with their role and quiz_saved status.
     * Supports both a raw JSON array ([...]) and a wrapper object ({"participants": [...]}).
     * Falls back to the local session roster if the endpoint returns 404 or is unreachable.
     */
    public ApiResult<List<Participant>> listParticipants(Long tripId) {
        ApiResult<JsonNode> raw = client.get(
                "/api/trips/" + tripId + "/participants",
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            List<Participant> parsed = parseParticipantList(raw.data());
            if (!parsed.isEmpty()) {
                LOCAL_PARTICIPANTS.put(tripId, new ArrayList<>(parsed));
                return ApiResult.ok(raw.status(), parsed);
            }
        }
        if (raw.isNotFound() || raw.status() == 0) {
            List<Participant> local = ensureLocalRoster(tripId);
            // Refresh quizSaved flags from local tracking
            List<Participant> synced = new ArrayList<>();
            for (Participant p : local) {
                boolean saved = p.quizSaved() || isQuizSavedLocally(tripId, p.userId());
                synced.add(new Participant(p.userId(), p.email(), p.fullName(), p.role(), saved));
            }
            LOCAL_PARTICIPANTS.put(tripId, synced);
            return ApiResult.ok(200, List.copyOf(synced));
        }
        return ApiResult.fail(raw.status(), raw.message());
    }

    /**
     * POST /api/trips/{tripId}/participants -> adds a traveler by email as a 'member'.
     */
    public ApiResult<Participant> addParticipant(Long tripId, String email) {
        return addParticipant(tripId, email, null);
    }

    public ApiResult<Participant> addParticipant(Long tripId, String email, String fullName) {
        String cleanEmail = email == null ? "" : email.trim();
        String cleanName = (fullName == null || fullName.isBlank()) ? deriveNameFromEmail(cleanEmail) : fullName.trim();
        AddParticipantRequest request = new AddParticipantRequest(cleanEmail, cleanName);

        ApiResult<JsonNode> raw = client.post(
                "/api/trips/" + tripId + "/participants",
                request,
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            JsonNode node = raw.data();
            Participant created;
            if (node == null || node.isNull()) {
                created = new Participant(NEXT_LOCAL_USER_ID.getAndIncrement(), cleanEmail, cleanName, "member", false);
            } else if (node.isArray() && !node.isEmpty()) {
                created = MAPPER.convertValue(node.get(node.size() - 1), Participant.class);
            } else if (node.has("participant") && node.get("participant").isObject()) {
                created = MAPPER.convertValue(node.get("participant"), Participant.class);
            } else {
                created = MAPPER.convertValue(node, Participant.class);
            }
            return ApiResult.ok(raw.status(), created);
        }

        // Local fallback when George's POST /api/trips/{id}/participants route isn't running yet (404 or offline)
        if (raw.isNotFound() || raw.status() == 0) {
            List<Participant> roster = ensureLocalRoster(tripId);
            for (Participant existing : roster) {
                if (existing.email() != null && existing.email().equalsIgnoreCase(cleanEmail)) {
                    return ApiResult.fail(409, "Traveler " + cleanEmail + " is already on this trip.");
                }
            }
            Participant created = new Participant(
                    NEXT_LOCAL_USER_ID.getAndIncrement(),
                    cleanEmail,
                    cleanName,
                    "member",
                    false
            );
            roster.add(created);
            return ApiResult.ok(201, created);
        }

        return ApiResult.fail(raw.status(), raw.message());
    }

    private static String deriveNameFromEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String prefix = email.substring(0, at).replace('.', ' ').replace('_', ' ').trim();
        if (prefix.isEmpty()) return email;
        return prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
    }

    private static List<Participant> parseParticipantList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        JsonNode arrayNode = node;
        if (node.isObject()) {
            if (node.has("participants") && node.get("participants").isArray()) {
                arrayNode = node.get("participants");
            } else if (node.has("travelers") && node.get("travelers").isArray()) {
                arrayNode = node.get("travelers");
            } else if (node.has("data") && node.get("data").isArray()) {
                arrayNode = node.get("data");
            }
        }
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<Participant> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            result.add(MAPPER.convertValue(item, Participant.class));
        }
        return result;
    }
}
