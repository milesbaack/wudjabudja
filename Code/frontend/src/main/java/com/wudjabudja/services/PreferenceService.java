package com.wudjabudja.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wudjabudja.models.PreferenceRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service to serialize, submit, and load traveler preferences (MVP-08 / MVP-02).
 * Uses Jackson for JSON serialization and Java 11 HttpClient for REST communication
 * with the Spring Boot backend at http://localhost:8080.
 */
public class PreferenceService {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache latest preferences in-memory for immediate UI access
    private static PreferenceRequest currentPreferences;

    public PreferenceService() {
        this(DEFAULT_BASE_URL);
    }

    public PreferenceService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serializes the preference request to JSON string.
     */
    public String serializeToJson(PreferenceRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    /**
     * Saves preferences by sending a POST request to Spring Boot:
     * POST /api/trips/{tripId}/preferences
     *
     * Per MVP-08: "Save and Load hit Spring Boot. A failed save shows as a failure."
     *
     * @param request The preference data payload.
     * @return SaveResult containing success flag and detailed feedback message.
     */
    public SaveResult savePreferences(PreferenceRequest request) {
        Long tripId = (request != null && request.getTripId() != null) ? request.getTripId() : 1L;

        try {
            String jsonPayload = serializeToJson(request);
            System.out.println("MVP-08 Sending POST /api/trips/" + tripId + "/preferences:\n" + jsonPayload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/trips/" + tripId + "/preferences"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                currentPreferences = request;
                return new SaveResult(true, "Preferences saved successfully to server!");
            } else {
                String errorBody = response.body() != null && !response.body().isBlank() ? " (" + response.body() + ")" : "";
                return new SaveResult(false, "Server rejected preferences with HTTP " + response.statusCode() + errorBody);
            }
        } catch (Exception e) {
            System.err.println("MVP-08 Save failed: " + e.getMessage());
            return new SaveResult(false, "Failed to connect to Spring Boot backend at " + baseUrl + ". Ensure the backend is running.");
        }
    }

    /**
     * Loads saved preferences from Spring Boot:
     * GET /api/trips/{tripId}/preferences?userId={userId}
     *
     * @param tripId The trip ID.
     * @param userId The user ID.
     * @return LoadResult containing success flag, message, and parsed PreferenceRequest if available.
     */
    public LoadResult loadPreferences(Long tripId, Long userId) {
        Long targetTripId = tripId != null ? tripId : 1L;
        Long targetUserId = userId != null ? userId : 1L;

        try {
            URI requestUri = URI.create(baseUrl + "/api/trips/" + targetTripId + "/preferences?userId=" + targetUserId);
            System.out.println("MVP-08 Sending GET " + requestUri);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body == null || body.isBlank()) {
                    return new LoadResult(false, "Server returned an empty response.", null);
                }
                PreferenceRequest pref = objectMapper.readValue(body, PreferenceRequest.class);
                currentPreferences = pref;
                return new LoadResult(true, "Preferences loaded successfully from server!", pref);
            } else {
                return new LoadResult(false, "Server returned HTTP " + response.statusCode() + " when loading preferences.", null);
            }
        } catch (Exception e) {
            System.err.println("MVP-08 Load failed: " + e.getMessage());
            return new LoadResult(false, "Failed to connect to Spring Boot backend at " + baseUrl + ". Ensure the backend is running.", null);
        }
    }

    public static PreferenceRequest getCurrentPreferences() {
        return currentPreferences;
    }

    public static void setCurrentPreferences(PreferenceRequest pref) {
        currentPreferences = pref;
    }

    public static class SaveResult {
        private final boolean success;
        private final String message;

        public SaveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class LoadResult {
        private final boolean success;
        private final String message;
        private final PreferenceRequest preferences;

        public LoadResult(boolean success, String message, PreferenceRequest preferences) {
            this.success = success;
            this.message = message;
            this.preferences = preferences;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public PreferenceRequest getPreferences() {
            return preferences;
        }
    }
}
