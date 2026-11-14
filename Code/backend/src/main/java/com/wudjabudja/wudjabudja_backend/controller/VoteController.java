
package com.wudjabudja.wudjabudja_backend.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/trips/{tripId}/votes")
public class VoteController {
    @PersistenceContext private EntityManager em;

    public record Ranking(
            @JsonProperty("option_id") Long optionId,
            Integer rank,
            Integer points
    ) {}

    public record VoteRequest(
            @JsonProperty("user_id") Long userId,
            @JsonAlias("votes") List<Ranking> rankings
    ) {}

    public record Winner(
            @JsonProperty("option_id") Long optionId,
            String date,
            @JsonProperty("time_block") String timeBlock,
            @JsonProperty("subgroup_participants") List<Long> subgroupParticipants,
            String title,
            String category,
            String neighborhood,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("estimated_cost_usd") BigDecimal estimatedCostUsd,
            @JsonProperty("total_points") Integer totalPoints,
            @JsonProperty("vote_count") Integer voteCount
    ) {}

    private void checkTrip(Long tripId) {
        if (((Number) em.createNativeQuery(
                "SELECT count(*) FROM trips WHERE id=:t"
        ).setParameter("t", tripId).getSingleResult()).longValue() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Trip not found"
            );
        }
    }

    private record Block(
            String date,
            String time,
            List<Long> subgroup
    ) {}

    private record Option(
            Long id,
            Block block,
            String title,
            String category,
            String neighborhood,
            String start,
            String end,
            BigDecimal cost
    ) {}

    private List<Long> subgroup(Object value) {
        if (value == null) return List.of();

        String json = value.toString().trim();

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalStateException(
                    "Invalid subgroup JSON: " + json
            );
        }

        String contents = json.substring(
                1, json.length() - 1
        ).trim();

        if (contents.isEmpty()) return List.of();

        try {
            List<Long> ids = new ArrayList<>();

            for (String item : contents.split(",")) {
                ids.add(Long.parseLong(item.trim()));
            }

            return ids.stream().sorted().toList();
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Invalid subgroup JSON: " + json, ex
            );
        }
    }

    private Map<Long, Option> options(Long tripId) {
        List<?> rows = em.createNativeQuery("""
            SELECT id, date::text, time_block,
                   subgroup_participants::text,
                   title, category, neighborhood,
                   start_time::text, end_time::text,
                   estimated_cost_usd
            FROM itinerary_options
            WHERE trip_id=:t
            ORDER BY id
            """)
                .setParameter("t", tripId)
                .getResultList();

        Map<Long, Option> out = new LinkedHashMap<>();

        for (Object row : rows) {
            Object[] r = (Object[]) row;

            Long id = ((Number) r[0]).longValue();

            Block block = new Block(
                    (String) r[1],
                    (String) r[2],
                    subgroup(r[3])
            );

            out.put(id, new Option(
                    id,
                    block,
                    (String) r[4],
                    (String) r[5],
                    (String) r[6],
                    (String) r[7],
                    (String) r[8],
                    (BigDecimal) r[9]
            ));
        }

        return out;
    }

    @PostMapping
    @Transactional
    public Map<String, String> submit(
            @PathVariable Long tripId,
            @RequestBody VoteRequest req
    ) {
        checkTrip(tripId);

        if (req == null ||
                req.userId() == null ||
                req.rankings() == null ||
                req.rankings().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User and rankings required"
            );
        }

        long member = ((Number) em.createNativeQuery("""
            SELECT count(*)
            FROM trip_participants
            WHERE trip_id=:t AND user_id=:u
            """)
                .setParameter("t", tripId)
                .setParameter("u", req.userId())
                .getSingleResult()).longValue();

        if (member == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not a participant on this trip"
            );
        }

        em.createNativeQuery(
                        "SELECT id FROM trips WHERE id=:t FOR UPDATE"
                )
                .setParameter("t", tripId)
                .getSingleResult();

        Map<Long, Option> options = options(tripId);
        Map<Block, Set<Integer>> ranks = new HashMap<>();
        Set<Long> seenOptions = new HashSet<>();

        for (Ranking r : req.rankings()) {
            if (r == null ||
                    r.optionId() == null ||
                    r.rank() == null ||
                    r.rank() < 1 ||
                    r.rank() > 3 ||
                    (r.points() != null &&
                            r.points() != 4 - r.rank())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid vote ranking"
                );
            }

            Option opt = options.get(r.optionId());

            if (opt == null ||
                    !seenOptions.add(r.optionId()) ||
                    !ranks.computeIfAbsent(
                            opt.block(), k -> new HashSet<>()
                    ).add(r.rank())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate rank or invalid option"
                );
            }

            if (!opt.block().subgroup().isEmpty() &&
                    !opt.block().subgroup().contains(req.userId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "User is not in option subgroup"
                );
            }
        }

        em.createNativeQuery("""
            DELETE FROM votes
            WHERE user_id=:u
              AND option_id IN (
                  SELECT id
                  FROM itinerary_options
                  WHERE trip_id=:t
              )
            """)
                .setParameter("u", req.userId())
                .setParameter("t", tripId)
                .executeUpdate();

        for (Ranking r : req.rankings()) {
            em.createNativeQuery("""
                INSERT INTO votes(option_id, user_id, rank, points)
                VALUES (:o, :u, :r, :p)
                """)
                    .setParameter("o", r.optionId())
                    .setParameter("u", req.userId())
                    .setParameter("r", r.rank())
                    .setParameter("p", 4 - r.rank())
                    .executeUpdate();
        }

        recalculate(tripId, options);

        return Map.of("message", "Votes saved");
    }

    private List<Winner> calculate(
            Long tripId,
            Map<Long, Option> options
    ) {
        List<?> rows = em.createNativeQuery("""
            SELECT v.option_id,
                   COALESCE(SUM(v.points), 0),
                   COUNT(*)
            FROM votes v
            JOIN itinerary_options o
              ON o.id=v.option_id
            WHERE o.trip_id=:t
            GROUP BY v.option_id
            """)
                .setParameter("t", tripId)
                .getResultList();

        Map<Long, int[]> totals = new HashMap<>();

        for (Object row : rows) {
            Object[] r = (Object[]) row;

            totals.put(
                    ((Number) r[0]).longValue(),
                    new int[] {
                            ((Number) r[1]).intValue(),
                            ((Number) r[2]).intValue()
                    }
            );
        }

        Map<Block, Option> winners = new LinkedHashMap<>();

        for (Option option : options.values()) {
            if (!totals.containsKey(option.id())) continue;

            Option old = winners.get(option.block());

            if (old == null ||
                    compare(option, old, totals) < 0) {
                winners.put(option.block(), option);
            }
        }

        List<Winner> out = new ArrayList<>();

        for (Option o : winners.values()) {
            int[] score = totals.get(o.id());

            out.add(new Winner(
                    o.id(),
                    o.block().date(),
                    o.block().time(),
                    o.block().subgroup(),
                    o.title(),
                    o.category(),
                    o.neighborhood(),
                    o.start(),
                    o.end(),
                    o.cost(),
                    score[0],
                    score[1]
            ));
        }

        return out;
    }

    private int compare(
            Option a,
            Option b,
            Map<Long, int[]> totals
    ) {
        int score = Integer.compare(
                totals.get(b.id())[0],
                totals.get(a.id())[0]
        );

        if (score != 0) return score;

        int cost = a.cost().compareTo(b.cost());

        return cost != 0
                ? cost
                : a.id().compareTo(b.id());
    }

    private void recalculate(
            Long tripId,
            Map<Long, Option> options
    ) {
        List<Winner> winners = calculate(tripId, options);

        em.createNativeQuery("""
            UPDATE itinerary_options
            SET is_final=false
            WHERE trip_id=:t
            """)
                .setParameter("t", tripId)
                .executeUpdate();

        for (Winner winner : winners) {
            em.createNativeQuery("""
                UPDATE itinerary_options
                SET is_final=true
                WHERE id=:id
                """)
                    .setParameter("id", winner.optionId())
                    .executeUpdate();
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Winner> winners(
            @PathVariable Long tripId
    ) {
        checkTrip(tripId);
        return calculate(tripId, options(tripId));
    }
}
