# Sprint 3 Backend Integration Guide: Group Travelers (`GRP-02`) & Voting (`VOTE-02`)

**Target Assignees:** George (`GRP-02`, `VOTE-02`) & Lukas (`VOTE-03`, `VOTE-04`)  
**Author:** Miles (`GRP-03` Shared Trip Screen, `VOTE-01` Voting Screen)

---

## Overview

The Sprint 3 JavaFX frontend (`GRP-03` in `TripsController.java` / `PreferenceController.java` and `VOTE-01` in `VotingController.java`) is wired to communicate with Spring Boot at `http://localhost:8080`.

To make integration effortless, the frontend models use Jackson `@JsonAlias` annotations so they accept both `snake_case` and `camelCase`, and both raw JSON arrays (`[...]`) and wrapped objects (`{"participants": [...]}` / `{"winners": [...]}`). Below are the exact routes, payloads, and ready-to-use Spring Boot DTO/Controller templates.

---

## 1. `GRP-02` — Add & List Travelers API

### Route 1A: `GET /api/trips/{tripId}/participants`
Lists every traveler on the trip (`trip_participants` joined with `users`), their role (`leader` or `member`), and whether they have saved quiz answers in `user_preferences` for this trip.

* **Response (`200 OK`):**
```json
[
  {
    "user_id": 1,
    "email": "demo@wudjabudja.local",
    "full_name": "Demo Traveler",
    "role": "leader",
    "quiz_saved": true
  },
  {
    "user_id": 2,
    "email": "alex@example.com",
    "full_name": "alex",
    "role": "member",
    "quiz_saved": false
  }
]
```

### Route 1B: `POST /api/trips/{tripId}/participants`
Adds a traveler by email to `trip_participants` with `role = 'member'`.
* If no row exists in `users` for that email, insert a new `users` row first (e.g., `password_hash = 'not-a-real-hash'`, `full_name` set to the part of the email before `@` if not provided).
* If the user is already in `trip_participants` for this `tripId`, throw `new ResponseStatusException(HttpStatus.BAD_REQUEST, "That traveler is already on this trip")` (or `409 CONFLICT`) so `GlobalExceptionHandler` returns `{"message": "..."}`.

* **Request Body:**
```json
{
  "email": "alex@example.com"
}
```
* **Response (`200 OK` or `201 Created`):**
```json
{
  "user_id": 2,
  "email": "alex@example.com",
  "full_name": "alex",
  "role": "member",
  "quiz_saved": false
}
```

### Ready-to-Use Backend DTOs (`com.wudjabudja.wudjabudja_backend.dto`)
```java
package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddParticipantRequest(
        @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName
) {}
```

```java
package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ParticipantResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("role") String role,
        @JsonProperty("quiz_saved") boolean quizSaved
) {}
```

---

## 2. `VOTE-02`, `VOTE-03` & `VOTE-04` — Submit Votes & Get Borda Winners API

### Route 2A: `POST /api/trips/{tripId}/votes`
Submits one traveler's ranked choices (`1st = 3 pts`, `2nd = 2 pts`, `3rd = 1 pt`).

* **Frontend Request Body (`VoteSubmissionRequest.java`):**
  *(Note: The frontend sends the list under **both** `"rankings"` and `"votes"` so your DTO can read either field name!)*
```json
{
  "user_id": 1,
  "rankings": [
    { "option_id": 101, "rank": 1, "points": 3 },
    { "option_id": 102, "rank": 2, "points": 2 },
    { "option_id": 103, "rank": 3, "points": 1 }
  ],
  "votes": [
    { "option_id": 101, "rank": 1, "points": 3 },
    { "option_id": 102, "rank": 2, "points": 2 },
    { "option_id": 103, "rank": 3, "points": 1 }
  ]
}
```

* **Validation Rules (`VOTE-03`):**
  1. Check `trip_participants` to verify `user_id` is on `tripId` (else `400 Bad Request` / `403 Forbidden` with `{"message": "User is not a participant on this trip"}`).
  2. Verify every `option_id` belongs to `tripId` and `rank` is `1`, `2`, or `3` (`points = 4 - rank`).
  3. Block duplicate `option_id`s or duplicate `rank`s within the same `(date, time_block, subgroup_participants)` block for that voter.
  4. Upsert/replace that traveler's votes in `votes` and trigger the Borda Count tally (`VOTE-04`).

* **Borda Tally & Tie-Breaker (`VOTE-04`):**
  1. For each `(date, time_block, subgroup_participants)` group in `itinerary_options` for `tripId`, sum `votes.points` per `option_id`.
  2. Pick the option with the **highest total points**.
  3. **Tie-breaker:** If two options in the same time block tie on total points, the option with the **lowest `estimated_cost_usd`** wins (and lowest `id` if costs are also equal).
  4. Update `itinerary_options.is_final = true` on the winning option for each block (and `false` on the non-winning options).

* **Response (`200 OK`):**
  Can return either `{"message": "Votes saved"}` or the updated list of winners (same shape as `GET /api/trips/{tripId}/votes` below). After `POST /api/trips/{tripId}/votes` succeeds, the frontend also calls `GET /api/trips/{tripId}/votes` and `GET /api/trips/{tripId}/itinerary` so both `VoteWinner` responses and `is_final = true` flags on `itinerary_options` are reflected automatically.

### Route 2B: `GET /api/trips/{tripId}/votes`
Returns the winning option for each time block (or an empty list `[]` if no votes have been cast yet).

* **Response (`200 OK`):**
```json
[
  {
    "option_id": 101,
    "date": "2027-03-12",
    "time_block": "morning",
    "subgroup_participants": [1, 2],
    "title": "Balboa Park Botanical Walk",
    "category": "outdoors",
    "neighborhood": "Balboa Park",
    "start_time": "09:30",
    "end_time": "11:30",
    "estimated_cost_usd": 15.00,
    "total_points": 6,
    "vote_count": 2
  }
]
```

### Ready-to-Use Backend DTOs (`com.wudjabudja.wudjabudja_backend.dto`)
```java
package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VoteSubmissionRequest(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("rankings") List<RankingItem> rankings
) {
    public record RankingItem(
            @JsonProperty("option_id") Long optionId,
            @JsonProperty("rank") Integer rank,
            @JsonProperty("points") Integer points
    ) {}
}
```

```java
package com.wudjabudja.wudjabudja_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record VoteWinnerResponse(
        @JsonProperty("option_id") Long optionId,
        @JsonProperty("date") String date,
        @JsonProperty("time_block") String timeBlock,
        @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,
        @JsonProperty("title") String title,
        @JsonProperty("category") String category,
        @JsonProperty("neighborhood") String neighborhood,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,
        @JsonProperty("total_points") Integer totalPoints,
        @JsonProperty("vote_count") Integer voteCount
) {}
```
