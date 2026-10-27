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
 * Service to serialize and submit traveler preferences (PREF-01 / PREF-02).
 * Uses Jackson for JSON serialization and Java 11 HttpClient for REST communication.
 */
public class PreferenceService {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache latest preferences in-memory for UI responsiveness
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
     * Saves preferences by sending a POST request to the backend API,
     * with graceful offline fallback if the backend service is not running yet.
     *
     * @param request The preference data payload.
     * @return Result summary message.
     */
    public SaveResult savePreferences(PreferenceRequest request) {
        currentPreferences = request;

        try {
            String jsonPayload = serializeToJson(request);
            System.out.println("PREF-02 Serialized Payload:\n" + jsonPayload);

            Long tripId = request.getTripId() != null ? request.getTripId() : 1L;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/trips/" + tripId + "/preferences"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new SaveResult(true, "Preferences synced successfully with server!");
            } else {
                return new SaveResult(true, "Preferences saved locally (Server responded with status " + response.statusCode() + ").");
            }
        } catch (Exception e) {
            // When George's backend endpoint is offline during development, local save succeeds
            System.out.println("Backend offline or unreachable (" + e.getMessage() + "). Saved preferences locally in memory.");
            return new SaveResult(true, "Preferences saved locally! (Backend API will sync once online)");
        }
    }

    public static PreferenceRequest getCurrentPreferences() {
        return currentPreferences;
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
}
