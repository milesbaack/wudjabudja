package com.wudjabudja.models;

/**
 * The 6 trip types the backend accepts, with friendly labels for the screen.
 * The value (e.g. "friends_trip") is what gets sent to Spring Boot.
 */
public enum TripType {
    GUYS("guys_trip", "Guys Trip"),
    GIRLS("girls_trip", "Girls Trip"),
    FRIENDS("friends_trip", "Friends Trip"),
    COUPLES("couples_trip", "Couples Trip"),
    FAMILY("family_trip", "Family Trip"),
    SOLO("solo_trip", "Solo Trip");

    private final String value;
    private final String label;

    TripType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    // "friends_trip" -> "Friends Trip". Unknown values are shown as-is.
    public static String labelFor(String value) {
        for (TripType type : values()) {
            if (type.value.equals(value)) return type.label;
        }
        return value;
    }

    // ComboBox shows whatever toString() returns
    @Override
    public String toString() {
        return label;
    }
}
