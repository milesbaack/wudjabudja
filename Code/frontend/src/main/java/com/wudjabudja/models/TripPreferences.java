package com.wudjabudja.models;

public record TripPreferences(
        String destination,
        String budget,
        String travelStyle,
        boolean familyFriendly
) {}