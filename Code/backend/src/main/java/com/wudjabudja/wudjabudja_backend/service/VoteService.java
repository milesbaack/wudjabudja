package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.VoteSubmissionRequest;
import com.wudjabudja.wudjabudja_backend.dto.VoteWinnerResponse;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOption;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOptionRepository;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import com.wudjabudja.wudjabudja_backend.model.TripParticipant;
import com.wudjabudja.wudjabudja_backend.model.TripParticipantRepository;
import com.wudjabudja.wudjabudja_backend.model.TripRepository;
import com.wudjabudja.wudjabudja_backend.model.Vote;
import com.wudjabudja.wudjabudja_backend.model.VoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Validates and saves Borda Count votes (VOTE-03) and tallies time-block winners with a lowest-cost tie-breaker (VOTE-04).
@Service
public class VoteService {

    private static final List<String> BLOCK_ORDER = List.of("morning", "afternoon", "evening");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final ItineraryOptionRepository options;
    private final VoteRepository votes;

    public VoteService(TripRepository trips,
                       TripParticipantRepository participants,
                       ItineraryOptionRepository options,
                       VoteRepository votes) {
        this.trips = trips;
        this.participants = participants;
        this.options = options;
        this.votes = votes;
    }

    // Saves one traveler's rankings for a trip, updates is_final on winners, and returns the winning options.
    @Transactional
    public List<VoteWinnerResponse> submitVotes(Long tripId, VoteSubmissionRequest request) {
        Trip trip = requireTrip(tripId);

        Set<Long> allowedVoters = participantUserIds(tripId, trip);
        List<ItineraryOption> tripOptions = options.findByTripIdOrderByIdAsc(tripId);
        if (tripOptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No itinerary options exist for trip " + tripId + ". Generate an itinerary first.");
        }

        List<String> problems = validateSubmission(tripId, allowedVoters, tripOptions, request);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }

        Map<Long, ItineraryOption> optionsById = new HashMap<>();
        Map<BlockKey, List<Long>> optionIdsByBlock = new HashMap<>();
        for (ItineraryOption opt : tripOptions) {
            optionsById.put(opt.getId(), opt);
            optionIdsByBlock.computeIfAbsent(BlockKey.of(opt), k -> new ArrayList<>()).add(opt.getId());
        }

        // Replace this traveler's previous votes in every time block included in their ballot
        Set<Long> optionIdsInVotedBlocks = new LinkedHashSet<>();
        for (VoteSubmissionRequest.RankingItem item : request.items()) {
            ItineraryOption opt = optionsById.get(item.optionId());
            if (opt != null) {
                optionIdsInVotedBlocks.addAll(optionIdsByBlock.getOrDefault(BlockKey.of(opt), List.of()));
            }
        }
        if (!optionIdsInVotedBlocks.isEmpty()) {
            votes.deleteByUserIdAndOptionIdIn(request.userId(), optionIdsInVotedBlocks);
        }

        List<Vote> toSave = new ArrayList<>();
        for (VoteSubmissionRequest.RankingItem item : request.items()) {
            int rank = item.rank();
            int points = bordaPoints(rank);
            toSave.add(new Vote(item.optionId(), request.userId(), rank, points));
        }
        votes.saveAll(toSave);

        List<Long> allOptionIds = tripOptions.stream().map(ItineraryOption::getId).toList();
        List<Vote> allVotes = votes.findByOptionIdInOrderByIdAsc(allOptionIds);
        List<VoteWinnerResponse> winners = tallyWinners(tripOptions, allVotes);
        options.saveAll(tripOptions);
        return winners;
    }

    // Returns the current Borda Count winning option for each time block on the trip.
    @Transactional
    public List<VoteWinnerResponse> loadWinners(Long tripId) {
        requireTrip(tripId);
        List<ItineraryOption> tripOptions = options.findByTripIdOrderByIdAsc(tripId);
        if (tripOptions.isEmpty()) {
            return List.of();
        }
        List<Long> allOptionIds = tripOptions.stream().map(ItineraryOption::getId).toList();
        List<Vote> allVotes = votes.findByOptionIdInOrderByIdAsc(allOptionIds);
        List<VoteWinnerResponse> winners = tallyWinners(tripOptions, allVotes);
        options.saveAll(tripOptions);
        return winners;
    }

    // ---------- VOTE-03: Validation ----------

    // Pure validation helper so VoteServiceTest can test every rule without a database.
    static List<String> validateSubmission(
            Long tripId,
            Set<Long> tripParticipantIds,
            List<ItineraryOption> tripOptions,
            VoteSubmissionRequest request) {

        List<String> problems = new ArrayList<>();
        if (request == null) {
            return List.of("Request body is required");
        }
        if (request.userId() == null) {
            problems.add("user_id is required");
        } else if (tripParticipantIds == null || !tripParticipantIds.contains(request.userId())) {
            problems.add("User " + request.userId() + " is not a participant on trip " + tripId);
        }

        List<VoteSubmissionRequest.RankingItem> items = request.items();
        if (items == null || items.isEmpty()) {
            problems.add("At least one vote ranking is required");
            return problems;
        }

        Map<Long, ItineraryOption> optionsById = new HashMap<>();
        for (ItineraryOption opt : orEmpty(tripOptions)) {
            if (opt.getId() != null) {
                optionsById.put(opt.getId(), opt);
            }
        }

        Set<Long> seenOptions = new HashSet<>();
        Map<BlockKey, Set<Integer>> seenRanksByBlock = new HashMap<>();

        for (VoteSubmissionRequest.RankingItem item : items) {
            if (item == null || item.optionId() == null) {
                problems.add("option_id is required for every ranking");
                continue;
            }
            if (item.rank() == null || item.rank() < 1 || item.rank() > 3) {
                problems.add("rank must be 1, 2, or 3 (got " + item.rank() + ")");
                continue;
            }
            ItineraryOption opt = optionsById.get(item.optionId());
            if (opt == null) {
                problems.add("Option " + item.optionId() + " does not belong to trip " + tripId);
                continue;
            }
            if (!seenOptions.add(item.optionId())) {
                problems.add("Duplicate vote for option " + item.optionId() + ": cannot rank the same option twice");
            }
            BlockKey blockKey = BlockKey.of(opt);
            Set<Integer> usedRanks = seenRanksByBlock.computeIfAbsent(blockKey, k -> new HashSet<>());
            if (!usedRanks.add(item.rank())) {
                problems.add("Duplicate rank " + item.rank() + " in " + opt.getDate() + " " + opt.getTimeBlock()
                        + ": cannot give two options the same rank in one time block");
            }
        }
        return problems;
    }

    // ---------- VOTE-04: Borda Tally & Lowest-Cost Tie-Breaker ----------

    // Tallies Borda points (1st = 3 pts, 2nd = 2, 3rd = 1) per time block and subgroup,
    // breaks ties by lowest estimated_cost_usd (then lowest id), and sets is_final = true on winners.
    static List<VoteWinnerResponse> tallyWinners(List<ItineraryOption> tripOptions, List<Vote> allVotes) {
        if (tripOptions == null || tripOptions.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> pointsByOption = new HashMap<>();
        Map<Long, Integer> countByOption = new HashMap<>();
        for (Vote vote : orEmpty(allVotes)) {
            if (vote.getOptionId() == null) continue;
            int pts = vote.getPoints() != null && vote.getPoints() > 0
                    ? vote.getPoints()
                    : bordaPoints(vote.getRank() == null ? 3 : vote.getRank());
            pointsByOption.merge(vote.getOptionId(), pts, Integer::sum);
            countByOption.merge(vote.getOptionId(), 1, Integer::sum);
        }

        // Sort options chronologically by date, morning -> afternoon -> evening, then id
        List<ItineraryOption> sorted = new ArrayList<>(tripOptions);
        sorted.sort(Comparator
                .comparing(ItineraryOption::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(o -> blockRank(o.getTimeBlock()))
                .thenComparing(ItineraryOption::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<BlockKey, List<ItineraryOption>> blocks = new LinkedHashMap<>();
        for (ItineraryOption opt : sorted) {
            opt.setFinal(false);
            blocks.computeIfAbsent(BlockKey.of(opt), k -> new ArrayList<>()).add(opt);
        }

        List<VoteWinnerResponse> winners = new ArrayList<>();
        for (List<ItineraryOption> blockOptions : blocks.values()) {
            ItineraryOption winner = pickBlockWinner(blockOptions, pointsByOption, countByOption);
            if (winner != null) {
                winner.setFinal(true);
                int totalPts = pointsByOption.getOrDefault(winner.getId(), 0);
                int voteCount = countByOption.getOrDefault(winner.getId(), 0);
                winners.add(toWinnerResponse(winner, totalPts, voteCount));
            }
        }
        return winners;
    }

    private static ItineraryOption pickBlockWinner(
            List<ItineraryOption> blockOptions,
            Map<Long, Integer> pointsByOption,
            Map<Long, Integer> countByOption) {

        ItineraryOption best = null;
        int bestPoints = 0;

        for (ItineraryOption candidate : blockOptions) {
            if (candidate.getId() == null) continue;
            int count = countByOption.getOrDefault(candidate.getId(), 0);
            int pts = pointsByOption.getOrDefault(candidate.getId(), 0);
            if (count <= 0 || pts <= 0) continue;

            if (best == null) {
                best = candidate;
                bestPoints = pts;
            } else if (pts > bestPoints) {
                best = candidate;
                bestPoints = pts;
            } else if (pts == bestPoints && isBetterTieBreaker(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    // Tie-breaker: lowest estimated_cost_usd wins; if costs are equal, lowest option id wins.
    private static boolean isBetterTieBreaker(ItineraryOption candidate, ItineraryOption currentBest) {
        BigDecimal candidateCost = candidate.getEstimatedCostUsd() == null ? BigDecimal.ZERO : candidate.getEstimatedCostUsd();
        BigDecimal bestCost = currentBest.getEstimatedCostUsd() == null ? BigDecimal.ZERO : currentBest.getEstimatedCostUsd();
        int costCompare = candidateCost.compareTo(bestCost);
        if (costCompare != 0) {
            return costCompare < 0;
        }
        if (candidate.getId() != null && currentBest.getId() != null) {
            return candidate.getId() < currentBest.getId();
        }
        return false;
    }

    static int bordaPoints(int rank) {
        return Math.max(1, 4 - rank);
    }

    private record BlockKey(LocalDate date, String timeBlock, List<Long> subgroupParticipants) {
        static BlockKey of(ItineraryOption opt) {
            return new BlockKey(
                    opt.getDate(),
                    opt.getTimeBlock(),
                    opt.getSubgroupParticipants() == null ? List.of() : List.copyOf(opt.getSubgroupParticipants())
            );
        }
    }

    private static VoteWinnerResponse toWinnerResponse(ItineraryOption row, int totalPoints, int voteCount) {
        return new VoteWinnerResponse(
                row.getId(),
                row.getDate() == null ? null : row.getDate().toString(),
                row.getTimeBlock(),
                row.getSubgroupParticipants() == null ? List.of() : List.copyOf(row.getSubgroupParticipants()),
                row.getSplitReason(),
                row.getTitle(),
                row.getDescription(),
                row.getCategory(),
                row.getNeighborhood(),
                row.getStartTime() == null ? null : row.getStartTime().format(HH_MM),
                row.getEndTime() == null ? null : row.getEndTime().format(HH_MM),
                row.getEstimatedCostUsd() == null ? null : row.getEstimatedCostUsd().setScale(2, RoundingMode.HALF_UP),
                totalPoints,
                voteCount
        );
    }

    private Set<Long> participantUserIds(Long tripId, Trip trip) {
        Set<Long> ids = new HashSet<>();
        if (trip.getCreatorId() != null) {
            ids.add(trip.getCreatorId());
        }
        for (TripParticipant tp : participants.findByTripIdOrderByIdAsc(tripId)) {
            if (tp.getUserId() != null) {
                ids.add(tp.getUserId());
            }
        }
        return ids;
    }

    private Trip requireTrip(Long tripId) {
        return trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"));
    }

    private static int blockRank(String timeBlock) {
        int i = timeBlock == null ? -1 : BLOCK_ORDER.indexOf(timeBlock);
        return i < 0 ? BLOCK_ORDER.size() : i;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
