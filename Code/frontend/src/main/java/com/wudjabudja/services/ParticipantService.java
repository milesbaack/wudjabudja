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
import java.util.List;

/**
 * Loads, adds, and removes travelers for a shared trip through Spring Boot (GRP-02 / GRP-03).
 * Routes:
 * - GET    /api/trips/{id}/participants
 * - POST   /api/trips/{id}/participants  (body: {"email": "..."})
 * - DELETE /api/trips/{id}/participants/{userId}
 */
public class ParticipantService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final BackendClient client = new BackendClient();

    /**
     * GET /api/trips/{tripId}/participants -> list of travelers with their role and quiz_saved status.
     * Supports both a raw JSON array ([...]) and a wrapper object ({"participants": [...]}).
     */
    public ApiResult<List<Participant>> listParticipants(Long tripId) {
        ApiResult<JsonNode> raw = client.get(
                "/api/trips/" + tripId + "/participants",
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            List<Participant> parsed = parseParticipantList(raw.data());
            return ApiResult.ok(raw.status(), parsed);
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
                created = new Participant(null, cleanEmail, cleanName, "member", false);
            } else if (node.isArray() && !node.isEmpty()) {
                created = MAPPER.convertValue(node.get(node.size() - 1), Participant.class);
            } else if (node.has("participant") && node.get("participant").isObject()) {
                created = MAPPER.convertValue(node.get("participant"), Participant.class);
            } else {
                created = MAPPER.convertValue(node, Participant.class);
            }
            return ApiResult.ok(raw.status(), AppSession.normalizeParticipant(created));
        }

        return ApiResult.fail(raw.status(), raw.message());
    }

    /**
     * DELETE /api/trips/{tripId}/participants/{userId} -> removes a member traveler from the trip.
     */
    public ApiResult<Void> removeParticipant(Long tripId, Long userId) {
        ApiResult<JsonNode> raw = client.delete(
                "/api/trips/" + tripId + "/participants/" + userId,
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            return ApiResult.ok(raw.status(), null);
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
            Participant p = MAPPER.convertValue(item, Participant.class);
            result.add(AppSession.normalizeParticipant(p));
        }
        return result;
    }
}
