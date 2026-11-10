package com.wudjabudja.storage;

import java.util.Map;

public record JsonProjectData(
        int schemaVersion,
        Map<String, JsonTrip> trips
) {
    public static JsonProjectData empty() {
        return new JsonProjectData(1, Map.of());
    }
}
