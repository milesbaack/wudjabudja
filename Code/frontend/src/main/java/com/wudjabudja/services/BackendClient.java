package com.wudjabudja.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Shared plumbing for talking to Spring Boot at http://localhost:8080.
 * Every failure comes back as an ApiResult with a plain-English message (never an exception),
 * using the backend's {"message": "..."} when it sends one.
 * Calls block, so run them off the JavaFX UI thread (in a javafx.concurrent.Task).
 */
public class BackendClient {

    public static final String BASE_URL = "http://localhost:8080";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    // Ignore fields we don't know about, so a backend that adds fields doesn't break the app
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public <T> ApiResult<T> get(String path, Duration timeout, TypeReference<T> type) {
        return send(HttpRequest.newBuilder().GET(), path, timeout, type);
    }

    public <T> ApiResult<T> post(String path, Object body, Duration timeout, TypeReference<T> type) {
        String json;
        try {
            json = body == null ? "" : MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            return ApiResult.fail(0, "Couldn't turn the request into JSON: " + e.getMessage());
        }
        return send(HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), path, timeout, type);
    }

    private <T> ApiResult<T> send(HttpRequest.Builder builder, String path, Duration timeout, TypeReference<T> type) {
        HttpRequest request = builder
                .uri(URI.create(BASE_URL + path))
                .header("Accept", "application/json")
                .timeout(timeout)
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            return ApiResult.fail(0, "Can't reach the backend at " + BASE_URL + ". Is it running? "
                    + "(In Code/backend: sh mvnw spring-boot:run)");
        } catch (HttpTimeoutException e) {
            return ApiResult.fail(0, "The backend didn't answer within " + describe(timeout) + ".");
        } catch (IOException e) {
            return ApiResult.fail(0, "Lost the connection to the backend: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.fail(0, "The request was cancelled.");
        }

        int status = response.statusCode();
        String body = response.body();
        if (status < 200 || status >= 300) {
            return ApiResult.fail(status, errorMessage(status, body));
        }
        try {
            return ApiResult.ok(status, MAPPER.readValue(body, type));
        } catch (IOException e) {
            return ApiResult.fail(status, "The backend's reply couldn't be read: " + e.getMessage());
        }
    }

    // Pull "message" out of the backend's error JSON; fall back to the status code
    private static String errorMessage(int status, String body) {
        try {
            JsonNode json = MAPPER.readTree(body);
            if (json != null && json.hasNonNull("message") && !json.get("message").asText().isBlank()) {
                return json.get("message").asText();
            }
        } catch (IOException ignored) {
            // not JSON, fall through
        }
        return "The backend returned HTTP " + status + ".";
    }

    private static String describe(Duration timeout) {
        return timeout.toMinutes() >= 1 ? timeout.toMinutes() + " minutes" : timeout.toSeconds() + " seconds";
    }
}
