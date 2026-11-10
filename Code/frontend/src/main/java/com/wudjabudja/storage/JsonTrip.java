package com.wudjabudja.storage;

// Lukas's local JSON storage trip (was models/Trip). Renamed so it doesn't clash with
// models.Trip, which is the trip the backend returns from /api/trips.
public record JsonTrip(
        String id,
        String userId,
        String name,
        JsonTripPreferences preferences
) {}
