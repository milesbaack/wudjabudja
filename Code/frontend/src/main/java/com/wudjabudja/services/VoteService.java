package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wudjabudja.models.VoteRanking;
import com.wudjabudja.models.VoteSubmissionRequest;
import com.wudjabudja.models.VoteWinner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Submits Borda Count rankings and loads winning options through Spring Boot (VOTE-01 / VOTE-02).
 * Routes:
 * - POST /api/trips/{id}/votes  (body: {"user_id": 1, "rankings": [{"option_id": 10, "rank": 1, "points": 3}, ...]})
 * - GET  /api/trips/{id}/votes  (returns each time block's winning options)
 *
 * If the backend route returns 404 or is offline before George merges VOTE-02, VoteService succeeds
 * locally so VotingController's built-in Borda Count + lowest-cost tie-breaker tally displays the winners.
 */
public class VoteService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final BackendClient client = new BackendClient();

    /**
     * POST /api/trips/{tripId}/votes -> submits one traveler's ranked choices.
     * If the backend also returns the updated winners in the POST response, they are parsed and returned.
     */
    public ApiResult<List<VoteWinner>> submitVotes(Long tripId, Long userId, List<VoteRanking> rankings) {
        VoteSubmissionRequest request = new VoteSubmissionRequest(userId, rankings);
        ApiResult<JsonNode> raw = client.post(
                "/api/trips/" + tripId + "/votes",
                request,
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            return ApiResult.ok(raw.status(), parseWinnerList(raw.data()));
        }
        // Allow local testing when George's POST /api/trips/{id}/votes route isn't running yet
        if (raw.isNotFound() || raw.status() == 0) {
            return ApiResult.ok(200, List.of());
        }
        return ApiResult.fail(raw.status(), raw.message());
    }

    /**
     * GET /api/trips/{tripId}/votes -> loads the winning options per time block.
     * Supports both a raw JSON array ([...]) and a wrapper object ({"winners": [...]}).
     */
    public ApiResult<List<VoteWinner>> loadWinners(Long tripId) {
        ApiResult<JsonNode> raw = client.get(
                "/api/trips/" + tripId + "/votes",
                TIMEOUT,
                new TypeReference<JsonNode>() {});
        if (raw.success()) {
            return ApiResult.ok(raw.status(), parseWinnerList(raw.data()));
        }
        if (raw.isNotFound() || raw.status() == 0) {
            return ApiResult.ok(200, List.of());
        }
        return ApiResult.fail(raw.status(), raw.message());
    }

    private static List<VoteWinner> parseWinnerList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        JsonNode arrayNode = node;
        if (node.isObject()) {
            if (node.has("winners") && node.get("winners").isArray()) {
                arrayNode = node.get("winners");
            } else if (node.has("results") && node.get("results").isArray()) {
                arrayNode = node.get("results");
            } else if (node.has("options") && node.get("options").isArray()) {
                arrayNode = node.get("options");
            } else if (node.has("data") && node.get("data").isArray()) {
                arrayNode = node.get("data");
            }
        }
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<VoteWinner> winners = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            winners.add(MAPPER.convertValue(item, VoteWinner.class));
        }
        return winners;
    }
}
