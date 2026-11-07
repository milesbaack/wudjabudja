package com.wudjabudja.models;

import java.util.Map;

public record ProjectData(
        int schemaVersion,
        Map<String, Trip> trips
) {
    public static ProjectData empty() {
        return new ProjectData(1, Map.of());
    }
}