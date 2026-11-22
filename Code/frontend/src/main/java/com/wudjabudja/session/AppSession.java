package com.wudjabudja.session;

import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;

/**
 * Remembers who is using the app, which trip they're working on, and which traveler
 * the trip creator is currently entering quiz answers or votes for (GRP-03 / VOTE-01).
 */
public final class AppSession {

    public static final long USER_ID = 1L;
    public static final String DEFAULT_EMAIL = "miles@example.com";
    public static final String DEFAULT_NAME = "Miles Baack";

    private static String loggedInEmail = DEFAULT_EMAIL;
    private static String loggedInName = DEFAULT_NAME;
    private static Trip currentTrip;          // null until a trip is created or opened
    private static Participant activeTraveler = defaultTraveler();
    private static String flashMessage;       // a one-time note for the next screen, e.g. "create a trip first"
    private static boolean flashIsError;

    private AppSession() {}

    public static void setLoggedInUser(String fullName, String email) {
        if (email != null && !email.isBlank()) {
            loggedInEmail = email.trim();
        }
        if (fullName != null && !fullName.isBlank()) {
            loggedInName = fullName.trim();
        } else if (email != null && !email.isBlank()) {
            loggedInName = deriveNameFromEmail(email.trim());
        }
        activeTraveler = defaultTraveler();
    }

    public static String getLoggedInName() {
        return loggedInName;
    }

    public static String getLoggedInEmail() {
        return loggedInEmail;
    }

    public static Participant defaultTraveler() {
        return new Participant(USER_ID, loggedInEmail, loggedInName, "leader", false);
    }

    /**
     * Replaces legacy "Demo Traveler" / "demo@wudjabudja.local" database values on user 1
     * with the active logged-in user's name and email.
     */
    public static Participant normalizeParticipant(Participant p) {
        if (p == null) return null;
        String email = p.email();
        String name = p.fullName();
        if (p.userId() != null && p.userId() == USER_ID) {
            if (email == null || email.isBlank() || "demo@wudjabudja.local".equalsIgnoreCase(email.trim())) {
                email = loggedInEmail;
            }
            if (name == null || name.isBlank() || "Demo Traveler".equalsIgnoreCase(name.trim())) {
                name = loggedInName;
            }
            return new Participant(p.userId(), email, name, p.role(), p.quizSaved());
        }
        return p;
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
        activeTraveler = participant != null ? normalizeParticipant(participant) : defaultTraveler();
    }

    public static void setFlashMessage(String message) {
        setFlashMessage(message, true);
    }

    public static void setFlashMessage(String message, boolean isError) {
        flashMessage = message;
        flashIsError = isError;
    }

    public static boolean isLastFlashError() {
        return flashIsError;
    }

    // Returns the message once, then forgets it
    public static String takeFlashMessage() {
        String message = flashMessage;
        flashMessage = null;
        return message;
    }

    public static void clear() {
        currentTrip = null;
        loggedInEmail = DEFAULT_EMAIL;
        loggedInName = DEFAULT_NAME;
        activeTraveler = defaultTraveler();
        flashMessage = null;
        flashIsError = false;
    }

    private static String deriveNameFromEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String prefix = email.substring(0, at).replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (prefix.isEmpty()) return email;
        String[] parts = prefix.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
