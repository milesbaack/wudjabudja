package com.wudjabudja.wudjabudja_backend.controller;

import com.wudjabudja.wudjabudja_backend.dto.AddParticipantRequest;
import com.wudjabudja.wudjabudja_backend.dto.ParticipantResponse;
import com.wudjabudja.wudjabudja_backend.dto.TripBriefing;
import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteWinnerResponse;
import com.wudjabudja.wudjabudja_backend.exception.GlobalExceptionHandler;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.service.ParticipantService;
import com.wudjabudja.wudjabudja_backend.service.TripService;
import com.wudjabudja.wudjabudja_backend.service.VoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the Sprint 3 HTTP routes (GRP-02 and VOTE-02) and their snake_case JSON contracts.
class Sprint3ControllerTest {

    private static MockMvc mvcFor(ParticipantService participantService, VoteService voteService) {
        return MockMvcBuilders
                .standaloneSetup(
                        new ParticipantController(participantService),
                        new VoteController(voteService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listParticipantsReturnsSnakeCaseJsonArray() throws Exception {
        ParticipantService stubParticipants = new ParticipantService(null, null, null, null) {
            @Override
            public List<ParticipantResponse> listForTrip(Long tripId) {
                return List.of(
                        new ParticipantResponse(1L, "demo@wudjabudja.local", "Demo Traveler", "leader", true),
                        new ParticipantResponse(2L, "alex@example.com", "Alex", "member", false)
                );
            }
        };
        MockMvc mvc = mvcFor(stubParticipants, new VoteService(null, null, null, null));

        mvc.perform(get("/api/trips/5/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user_id").value(1))
                .andExpect(jsonPath("$[0].email").value("demo@wudjabudja.local"))
                .andExpect(jsonPath("$[0].full_name").value("Demo Traveler"))
                .andExpect(jsonPath("$[0].role").value("leader"))
                .andExpect(jsonPath("$[0].quiz_saved").value(true))
                .andExpect(jsonPath("$[1].user_id").value(2))
                .andExpect(jsonPath("$[1].role").value("member"))
                .andExpect(jsonPath("$[1].quiz_saved").value(false));
    }

    @Test
    void addParticipantReturns201WithCreatedMember() throws Exception {
        ParticipantService stubParticipants = new ParticipantService(null, null, null, null) {
            @Override
            public ParticipantResponse addByEmail(Long tripId, AddParticipantRequest request) {
                return new ParticipantResponse(2L, request.email(), "Alex", "member", false);
            }
        };
        MockMvc mvc = mvcFor(stubParticipants, new VoteService(null, null, null, null));

        mvc.perform(post("/api/trips/5/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(2))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.full_name").value("Alex"))
                .andExpect(jsonPath("$.role").value("member"))
                .andExpect(jsonPath("$.quiz_saved").value(false));
    }

    @Test
    void duplicateParticipantReturns409WithJsonMessage() throws Exception {
        ParticipantService stubParticipants = new ParticipantService(null, null, null, null) {
            @Override
            public ParticipantResponse addByEmail(Long tripId, AddParticipantRequest request) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Traveler " + request.email() + " is already on this trip");
            }
        };
        MockMvc mvc = mvcFor(stubParticipants, new VoteService(null, null, null, null));

        mvc.perform(post("/api/trips/5/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Traveler alex@example.com is already on this trip"));
    }

    @Test
    void removeParticipantReturns200Or400ForLeader() throws Exception {
        ParticipantService stubParticipants = new ParticipantService(null, null, null, null) {
            @Override
            public void removeParticipant(Long tripId, Long userId) {
                if (userId != null && userId == 1L) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The trip leader cannot be removed from the trip");
                }
            }
        };
        MockMvc mvc = mvcFor(stubParticipants, new VoteService(null, null, null, null));

        mvc.perform(delete("/api/trips/5/participants/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(2))
                .andExpect(jsonPath("$.message").value("Traveler removed from trip"));

        mvc.perform(delete("/api/trips/5/participants/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The trip leader cannot be removed from the trip"));
    }

    @Test
    void submitAndLoadVotesReturnSnakeCaseWinners() throws Exception {
        List<VoteWinnerResponse> sampleWinners = List.of(
                new VoteWinnerResponse(
                        102L, "2027-03-12", "morning", List.of(1L, 2L), null,
                        "Balboa Park Botanical Walk", "Morning stroll", "outdoors", "Balboa Park",
                        "09:30", "11:30", new BigDecimal("15.00"), 5, 2
                )
        );
        VoteService stubVotes = new VoteService(null, null, null, null) {
            @Override
            public List<VoteWinnerResponse> submitVotes(Long tripId, VoteSubmissionRequest request) {
                return sampleWinners;
            }

            @Override
            public List<VoteWinnerResponse> loadWinners(Long tripId) {
                return sampleWinners;
            }
        };
        MockMvc mvc = mvcFor(new ParticipantService(null, null, null, null), stubVotes);

        mvc.perform(post("/api/trips/5/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":1,\"rankings\":[{\"option_id\":102,\"rank\":1,\"points\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].option_id").value(102))
                .andExpect(jsonPath("$[0].time_block").value("morning"))
                .andExpect(jsonPath("$[0].subgroup_participants[0]").value(1))
                .andExpect(jsonPath("$[0].subgroup_participants[1]").value(2))
                .andExpect(jsonPath("$[0].estimated_cost_usd").value(15.00))
                .andExpect(jsonPath("$[0].total_points").value(5))
                .andExpect(jsonPath("$[0].vote_count").value(2));

        mvc.perform(get("/api/trips/5/votes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].option_id").value(102))
                .andExpect(jsonPath("$[0].title").value("Balboa Park Botanical Walk"));
    }

    @Test
    void loadBriefingReturnsSavedBriefingOr404WhenMissing() throws Exception {
        TripBriefing sampleBriefing = new TripBriefing(
                List.of(new TripBriefing.CustomTip("No glass on beaches", "City rule")),
                new TripBriefing.LanguageGuide("English", List.of(new TripBriefing.Phrase("Cali Burrito", "Fries in burrito"))),
                "Sunny 68F",
                List.of(new TripBriefing.PackingItem("Sunglasses", "Sunny coast")),
                List.of("Founded in 1769."),
                new TripBriefing.SeasonAdvisory("medium", "Book dinners ahead."),
                List.of(new TripBriefing.SouvenirIdea(1L, "Padres hat", new BigDecimal("30.00")))
        );

        TripService stubTripService = new TripService(null, null, null) {
            @Override
            public Trip get(Long id) {
                Trip t = new Trip();
                t.setId(id);
                if (id == 5L) {
                    t.setBriefingData(sampleBriefing);
                }
                return t;
            }
        };

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new BriefingController(stubTripService, null, null, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/trips/5/briefing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customs[0].tip").value("No glass on beaches"))
                .andExpect(jsonPath("$.language.primary_language").value("English"))
                .andExpect(jsonPath("$.season_advisory.crowd_level").value("medium"))
                .andExpect(jsonPath("$.souvenirs[0].user_id").value(1));

        mvc.perform(get("/api/trips/6/briefing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No trip briefing generated yet for trip 6. Generate one first."));
    }
}
