package com.wudjabudja.models;

public record Trip(
        String id,
        String userId,
        String name,
        TripPreferences preferences
) {}