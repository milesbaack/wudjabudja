package com.wudjabudja.services;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.PreferenceRequest;
import com.wudjabudja.models.Trip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Provides an instant local Demo Group Trip (San Diego 2-day group trip with 2 seeded travelers,
 * subgroup splits, and 3 candidate options per time block) so GRP-03 and VOTE-01 can be tested
 * in JavaFX even when PostgreSQL / Spring Boot / Gemini are not running.
 */
public final class DemoTripSupport {

    public static final long DEMO_TRIP_ID = 999L;

    private static Trip demoTrip;
    private static Itinerary demoItinerary;

    private DemoTripSupport() {}

    public static boolean isDemoTrip(Long tripId) {
        return tripId != null && tripId == DEMO_TRIP_ID;
    }

    public static Trip activateDemoGroupTrip() {
        demoTrip = new Trip(
                DEMO_TRIP_ID,
                1L,
                "San Diego Group Weekend (Demo)",
                "San Diego, CA",
                "2027-03-12",
                "2027-03-13",
                new BigDecimal("2400.00"),
                "friends_trip",
                "WUDJA-DEMO99"
        );

        // Seed 2 travelers and their preferences so the creator can immediately test adding a 3rd,
        // editing quizzes, viewing subgroup splits, and casting Borda Count votes.
        PreferenceService.seedDemoPreferences(new PreferenceRequest(
                DEMO_TRIP_ID, 1L, "08:00", "intermediate", 15,
                Map.of("transit", 60.0, "lodging", 300.0, "food", 200.0, "activities", 150.0),
                new BigDecimal("40"),
                List.of("food", "outdoors", "sports")
        ));
        ParticipantService ps = new ParticipantService();
        ps.addParticipant(DEMO_TRIP_ID, "alex@example.com", "Alex Rivera");
        PreferenceService.seedDemoPreferences(new PreferenceRequest(
                DEMO_TRIP_ID, 2L, "09:30", "expert", 20,
                Map.of("transit", 80.0, "lodging", 300.0, "food", 220.0, "activities", 180.0),
                new BigDecimal("50"),
                List.of("culture", "food", "nightlife")
        ));

        demoItinerary = buildSampleGroupItinerary();
        return demoTrip;
    }

    public static Trip getDemoTrip() {
        return demoTrip;
    }

    public static Itinerary getDemoItinerary() {
        if (demoItinerary == null) {
            demoItinerary = buildSampleGroupItinerary();
        }
        return demoItinerary;
    }

    public static Itinerary regenerateDemoItinerary() {
        demoItinerary = buildSampleGroupItinerary();
        return demoItinerary;
    }

    private static Itinerary buildSampleGroupItinerary() {
        String nowUtc = OffsetDateTime.now(ZoneOffset.UTC).toString();

        // Day 1 Morning: Everyone together
        Itinerary.Subgroup d1MorningAll = new Itinerary.Subgroup(
                List.of(1L, 2L),
                null,
                List.of(
                        new Itinerary.Option(9001L, "La Jolla Cove Coastal Walk & Sea Lions",
                                "Scenic cliffside trail overlooking sea lions, tide pools, and ocean bluffs.",
                                "outdoors", "La Jolla", "09:30", "11:30", new BigDecimal("0.00"),
                                "Fits everyone's morning schedule after 09:30 wake-up buffer and outdoor interest.", false),
                        new Itinerary.Option(9002L, "Little Italy Mercato & Espresso Crawl",
                                "Stroll through artisan food stalls, local roasters, and fresh pastries.",
                                "food", "Little Italy", "09:30", "11:30", new BigDecimal("18.00"),
                                "Matches shared food interest well under the $40 per-activity cap.", false),
                        new Itinerary.Option(9003L, "USS Midway Museum Flight Deck Tour",
                                "Explore historic naval aircraft and interactive flight simulators on the harbour.",
                                "culture", "Embarcadero", "10:00", "12:00", new BigDecimal("32.00"),
                                "Great waterfront cultural stop within the group's radius and budget.", false)
                )
        );

        // Day 1 Afternoon: Subgroup split (Sports/Outdoors vs Culture/Museums)
        Itinerary.Subgroup d1AfternoonSub1 = new Itinerary.Subgroup(
                List.of(1L),
                "Subgroup split: Traveler 1 wanted live sports & outdoors while Traveler 2 preferred Balboa Park museums",
                List.of(
                        new Itinerary.Option(9004L, "Petco Park Behind-the-Scenes Stadium Tour",
                                "Guided walk through the dugout, press box, and field warning track.",
                                "sports", "East Village", "13:30", "15:30", new BigDecimal("25.00"),
                                "Tailored for sports fans wanting a stadium experience.", false),
                        new Itinerary.Option(9005L, "Coronado Beach Bike Loop",
                                "Cruiser bike rental along the bayfront path and Hotel del Coronado.",
                                "outdoors", "Coronado", "13:30", "16:00", new BigDecimal("22.00"),
                                "Active outdoor coastal ride with easy flat terrain.", false),
                        new Itinerary.Option(9006L, "Mission Bay Kayak Rental",
                                "Calm bay paddling with views of the skyline and marinas.",
                                "outdoors", "Mission Bay", "14:00", "16:00", new BigDecimal("30.00"),
                                "Hits outdoor adventure right at the $30 mark.", false)
                )
        );

        Itinerary.Subgroup d1AfternoonSub2 = new Itinerary.Subgroup(
                List.of(2L),
                "Subgroup split: Culture & museum focus in Balboa Park",
                List.of(
                        new Itinerary.Option(9007L, "San Diego Museum of Art & Alcazar Garden",
                                "Spanish Renaissance architecture, curated galleries, and Moorish gardens.",
                                "culture", "Balboa Park", "13:30", "15:30", new BigDecimal("20.00"),
                                "Matches culture preference with walkable gardens.", false),
                        new Itinerary.Option(9008L, "Japanese Friendship Garden Tea & Bonsai Walk",
                                "Koi ponds, canyon views, and serene pathways through Balboa Park.",
                                "relaxation", "Balboa Park", "13:30", "15:30", new BigDecimal("14.00"),
                                "Low-cost cultural & relaxing afternoon option.", false),
                        new Itinerary.Option(9009L, "Old Town Historic Adobe Walking Tour",
                                "Explore early California heritage buildings, plazas, and artisan shops.",
                                "tour", "Old Town", "14:00", "16:00", new BigDecimal("20.00"),
                                "Tie-cost test option ($20 vs $20) to demonstrate Borda tie-breaker.", false)
                )
        );

        // Day 1 Evening: Everyone reunites for dinner & nightlife
        Itinerary.Subgroup d1EveningAll = new Itinerary.Subgroup(
                List.of(1L, 2L),
                null,
                List.of(
                        new Itinerary.Option(9010L, "Gaslamp Quarter Taco & Mezcal Tasting",
                                "Sample Baja-style fish tacos and craft aguas frescas across three local spots.",
                                "food", "Gaslamp Quarter", "18:30", "21:00", new BigDecimal("35.00"),
                                "Brings the whole group back together for food and lively evening atmosphere.", false),
                        new Itinerary.Option(9011L, "North Park Craft Brewery & Rooftop Social",
                                "Casual neighborhood hop featuring local San Diego IPAs and street food.",
                                "nightlife", "North Park", "19:00", "21:30", new BigDecimal("28.00"),
                                "Fun group nightlife option under budget.", false),
                        new Itinerary.Option(9012L, "Sunset Cliffs Golden Hour & Ocean Beach Pier",
                                "Watch the Pacific sunset from the dramatic cliffs followed by casual boardwalk tacos.",
                                "outdoors", "Sunset Cliffs", "18:00", "20:30", new BigDecimal("15.00"),
                                "Budget-friendly scenic evening for the whole group.", false)
                )
        );

        Itinerary.Day day1 = new Itinerary.Day(
                "2027-03-12",
                List.of(
                        new Itinerary.Block("morning", List.of(d1MorningAll)),
                        new Itinerary.Block("afternoon", List.of(d1AfternoonSub1, d1AfternoonSub2)),
                        new Itinerary.Block("evening", List.of(d1EveningAll))
                )
        );

        return new Itinerary(DEMO_TRIP_ID, nowUtc, List.of(day1));
    }
}
