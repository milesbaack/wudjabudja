package com.wudjabudja.session;

import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;

/**
 * Remembers who is using the app, which trip they're working on, and which traveler
 * the trip creator is currently entering quiz answers or votes for (GRP-03 / VOTE-01).
 */
public final class AppSession {

    public static final long USER_ID = 1L;
    public static final String DEFAULT_EMAIL = "demo@wudjabudja.local";
    public static final String DEFAULT_NAME = "Demo Traveler";

    private static Trip currentTrip;          // null until a trip is created or opened
    private static Participant activeTraveler = defaultTraveler();
    private static String flashMessage;       // a one-time note for the next screen, e.g. "create a trip first"

    private AppSession() {}

    public static Participant defaultTraveler() {
        return new Participant(USER_ID, DEFAULT_EMAIL, DEFAULT_NAME, "leader", false);
    }

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
        boolean tripChanged = (currentTrip == null && trip != null)
                || (currentTrip != null && (trip == null || !currentTrip.id().equals(trip.id())));
        currentTrip = trip;
        if (tripChanged) {
            activeTraveler = defaultTraveler();
        }
    }

    public static Participant getActiveTraveler() {
        return activeTraveler != null ? activeTraveler : defaultTraveler();
    }

    public static Long getActiveUserId() {
        return activeTraveler != null && activeTraveler.userId() != null
                ? activeTraveler.userId()
                : USER_ID;
    }

    public static void setActiveTraveler(Participant participant) {
        activeTraveler = participant != null ? participant : defaultTraveler();
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
        activeTraveler = defaultTraveler();
        flashMessage = null;
    }
}
