package com.wudjabudja.storage;

public record JsonTripPreferences(
        String destination,
        String budget,
        String travelStyle,
        boolean familyFriendly
) {}
