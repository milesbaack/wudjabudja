package com.wudjabudja.session;

import com.wudjabudja.models.Trip;

/**
 * Remembers who is using the app and which trip they're working on, while the app is open.
 * Sprint 1 is single-user: everyone is user 1 (seeded by Code/database/seed.sql) until real login lands.
 */
public final class AppSession {

    public static final long USER_ID = 1L;

    private static Trip currentTrip;      // null until a trip is created or opened
    private static String flashMessage;   // a one-time note for the next screen, e.g. "create a trip first"

    private AppSession() {}

    public static Trip getCurrentTrip() {
        return currentTrip;
    }

    public static Long getTripId() {
        return currentTrip == null ? null : currentTrip.id();
    }

    public static boolean hasTrip() {
        return currentTrip != null;
    }

    public static void setCurrentTrip(Trip trip) {
        currentTrip = trip;
    }

    public static void setFlashMessage(String message) {
        flashMessage = message;
    }

    // Returns the message once, then forgets it
    public static String takeFlashMessage() {
        String message = flashMessage;
        flashMessage = null;
        return message;
    }

    public static void clear() {
        currentTrip = null;
        flashMessage = null;
    }
}
