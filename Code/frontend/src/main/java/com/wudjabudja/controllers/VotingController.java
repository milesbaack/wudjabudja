package com.wudjabudja.controllers;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripType;
import com.wudjabudja.models.VoteRanking;
import com.wudjabudja.models.VoteWinner;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.ItineraryService;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.TripService;
import com.wudjabudja.services.VoteService;
import com.wudjabudja.session.AppSession;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the Group Voting Screen (VOTE-01).
 * - Trip creator picks which traveler is voting from the dropdown.
 * - Traveler ranks options 1st (3 pts), 2nd (2 pts), 3rd (1 pt) in each time block.
 * - Prevents duplicate ranks in the same time block.
 * - Submits votes via POST /api/trips/{id}/votes (VOTE-02) and displays Borda Count winners.
 */
public class VotingController implements Initializable {

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "morning", "🌅  Morning",
            "afternoon", "☀️  Afternoon",
            "evening", "🌙  Evening"
    );

    private static final Map<String, String[]> CATEGORY_COLORS = Map.of(
            "food", new String[]{"#fef3c7", "#92400e"},
            "outdoors", new String[]{"#dcfce7", "#166534"},
            "culture", new String[]{"#ede9fe", "#5b21b6"},
            "nightlife", new String[]{"#e0e7ff", "#3730a3"},
            "sports", new String[]{"#fee2e2", "#991b1b"},
            "shopping", new String[]{"#fce7f3", "#9d174d"},
            "relaxation", new String[]{"#e0f2fe", "#075985"},
            "tour", new String[]{"#f1f5f9", "#334155"}
    );

    private static final String DAY_CARD_STYLE =
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; " +
            "-fx-padding: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 8, 0, 0, 2);";

    // Remembers each traveler's rankings in memory across screen switches:
    // tripId -> (userId -> (optionId -> rank 1..3))
    private static final Map<Long, Map<Long, Map<Long, Integer>>> SESSION_BALLOTS = new HashMap<>();

    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private ComboBox<Participant> voterCombo;
    @FXML private Label progressLabel;
    @FXML private Button autoRankBtn;
    @FXML private Button clearRanksBtn;
    @FXML private Button submitVotesBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private Label statusLabel;

    @FXML private VBox winnersCard;
    @FXML private Label winnersSubtitleLabel;
    @FXML private VBox winnersListBox;

    @FXML private VBox emptyStateBox;
    @FXML private VBox daysVotingBox;

    private final TripService tripService = new TripService();
    private final ParticipantService participantService = new ParticipantService();
    private final ItineraryService itineraryService = new ItineraryService();
    private final VoteService voteService = new VoteService();

    private Trip trip;
    private Itinerary itinerary;
    private List<Participant> participants = new ArrayList<>();
    private final Map<Long, Participant> participantById = new HashMap<>();
    private List<VoteWinner> apiWinners = new ArrayList<>();
    private final Set<Long> winningOptionIds = new HashSet<>();
    private final Map<Long, Integer> winningOptionPoints = new HashMap<>();

    // Current voter's selections: optionId -> rank (1, 2, or 3)
    private final Map<Long, Integer> currentRanks = new LinkedHashMap<>();
    // Grouping of optionIds by subgroup block key so we can enforce unique ranks 1, 2, 3 per block
    private final Map<String, List<Itinerary.Option>> blockOptionsByKey = new LinkedHashMap<>();
    private boolean updatingVoterCombo = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create or open one first.", true);
            setControlsDisabled(true);
            return;
        }

        showTripHeader(trip);
        loadVotingData();
    }

    private record VotingLoadBundle(
            ApiResult<Trip> tripResult,
            ApiResult<List<Participant>> participantsResult,
            ApiResult<Itinerary> itineraryResult,
            ApiResult<List<VoteWinner>> winnersResult
    ) {}

    private void loadVotingData() {
        if (trip == null) return;
        Long tripId = trip.id();
        setBusy(true, "Loading itinerary options and group votes...");

        Task<VotingLoadBundle> task = new Task<>() {
            @Override
            protected VotingLoadBundle call() {
                ApiResult<Trip> tRes = tripService.getTrip(tripId);
                ApiResult<List<Participant>> pRes = participantService.listParticipants(tripId);
                ApiResult<Itinerary> iRes = itineraryService.loadItinerary(tripId);
                ApiResult<List<VoteWinner>> wRes = voteService.loadWinners(tripId);
                return new VotingLoadBundle(tRes, pRes, iRes, wRes);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            VotingLoadBundle bundle = task.getValue();

            if (bundle.tripResult().success() && bundle.tripResult().data() != null) {
                trip = bundle.tripResult().data();
                AppSession.setCurrentTrip(trip);
                showTripHeader(trip);
            }

            if (bundle.participantsResult().success() && bundle.participantsResult().data() != null
                    && !bundle.participantsResult().data().isEmpty()) {
                this.participants = List.copyOf(bundle.participantsResult().data());
            } else {
                this.participants = List.of(AppSession.defaultTraveler());
            }

            participantById.clear();
            for (Participant p : this.participants) {
                if (p.userId() != null) {
                    participantById.put(p.userId(), p);
                }
            }

            populateVoterCombo();

            if (bundle.winnersResult() != null && bundle.winnersResult().success() && bundle.winnersResult().data() != null) {
                this.apiWinners = bundle.winnersResult().data();
            } else {
                this.apiWinners = List.of();
            }

            ApiResult<Itinerary> itRes = bundle.itineraryResult();
            if (itRes != null && itRes.success() && itRes.data() != null) {
                this.itinerary = itRes.data();
                loadBallotForActiveVoter();
                syncWinningOptions();
                renderWinnersSection();
                renderVotingBlocks();
            } else if (itRes != null && itRes.isNotFound()) {
                this.itinerary = null;
                showEmptyState();
            } else if (itRes != null) {
                showStatus("✗ " + itRes.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("✗ Failed to load voting data: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "voting-load");
    }

    private void populateVoterCombo() {
        updatingVoterCombo = true;
        voterCombo.getItems().setAll(participants);
        Participant selected = participants.stream()
                .filter(p -> p.userId() != null && p.userId().equals(AppSession.getActiveUserId()))
                .findFirst()
                .orElse(participants.get(0));
        AppSession.setActiveTraveler(selected);
        voterCombo.setValue(selected);
        updatingVoterCombo = false;
    }

    @FXML
    public void handleVoterChanged(ActionEvent event) {
        if (updatingVoterCombo) return;
        Participant selected = voterCombo.getValue();
        if (selected == null || selected.userId() == null) return;

        saveBallotToSession();
        AppSession.setActiveTraveler(selected);
        loadBallotForActiveVoter();
        clearStatus();
        renderVotingBlocks();
    }

    // ---------- Ranking Logic (1st = 3 pts, 2nd = 2 pts, 3rd = 1 pt) ----------

    private void assignRank(String blockKey, Itinerary.Option targetOption, int newRank) {
        if (targetOption.id() == null) {
            showStatus("✗ Option is missing a database ID. Regenerate the itinerary first.", true);
            return;
        }
        List<Itinerary.Option> siblingOptions = blockOptionsByKey.getOrDefault(blockKey, List.of());
        Integer previousRankOnTarget = currentRanks.get(targetOption.id());

        // Clicking the already-selected rank toggles it off
        if (previousRankOnTarget != null && previousRankOnTarget == newRank) {
            currentRanks.remove(targetOption.id());
        } else {
            // If another option in this same time block already has 'newRank', either swap or clear it
            for (Itinerary.Option sibling : siblingOptions) {
                if (sibling.id() == null || sibling.id().equals(targetOption.id())) continue;
                Integer sibRank = currentRanks.get(sibling.id());
                if (sibRank != null && sibRank == newRank) {
                    if (previousRankOnTarget != null) {
                        currentRanks.put(sibling.id(), previousRankOnTarget);
                    } else {
                        currentRanks.remove(sibling.id());
                    }
                }
            }
            currentRanks.put(targetOption.id(), newRank);
        }

        saveBallotToSession();
        renderVotingBlocks();
    }

    @FXML
    public void handleAutoRank(ActionEvent event) {
        if (itinerary == null) return;
        for (Map.Entry<String, List<Itinerary.Option>> entry : blockOptionsByKey.entrySet()) {
            List<Itinerary.Option> opts = entry.getValue();
            boolean fullyRanked = opts.stream()
                    .allMatch(o -> o.id() != null && currentRanks.containsKey(o.id()));
            if (fullyRanked) continue;

            // Clear partial ranks in this block and rank 1st, 2nd, 3rd (cheapest first for deterministic demoing)
            for (Itinerary.Option o : opts) {
                if (o.id() != null) currentRanks.remove(o.id());
            }
            List<Itinerary.Option> sorted = new ArrayList<>(opts);
            sorted.sort(Comparator.comparing(
                    o -> o.estimatedCostUsd() == null ? BigDecimal.ZERO : o.estimatedCostUsd()
            ));
            for (int i = 0; i < sorted.size() && i < 3; i++) {
                if (sorted.get(i).id() != null) {
                    currentRanks.put(sorted.get(i).id(), i + 1);
                }
            }
        }
        saveBallotToSession();
        renderVotingBlocks();
        showStatus("✓ Auto-ranked all time blocks (1st, 2nd, 3rd) for " + AppSession.getActiveTraveler().shortName()
                + ". Click \"Submit Votes\" when ready!", false);
    }

    @FXML
    public void handleClearRanks(ActionEvent event) {
        currentRanks.clear();
        saveBallotToSession();
        clearStatus();
        renderVotingBlocks();
    }

    // ---------- Submitting Votes & Refreshing Winners ----------

    @FXML
    public void handleSubmitVotes(ActionEvent event) {
        if (trip == null || itinerary == null) return;

        // Validate that at least one block is ranked and no ranked block has incomplete/duplicate ranks
        List<String> incompleteBlocks = new ArrayList<>();
        List<VoteRanking> rankings = new ArrayList<>();

        for (Map.Entry<String, List<Itinerary.Option>> entry : blockOptionsByKey.entrySet()) {
            List<Itinerary.Option> opts = entry.getValue();
            int expectedCount = Math.min(3, opts.size());
            Set<Integer> usedRanks = new HashSet<>();
            int rankedInBlock = 0;

            for (Itinerary.Option o : opts) {
                if (o.id() == null) continue;
                Integer r = currentRanks.get(o.id());
                if (r != null) {
                    rankedInBlock++;
                    usedRanks.add(r);
                    rankings.add(VoteRanking.of(o.id(), r));
                }
            }

            if (rankedInBlock > 0 && (rankedInBlock < expectedCount || usedRanks.size() != rankedInBlock)) {
                incompleteBlocks.add(entry.getKey());
            }
        }

        if (rankings.isEmpty()) {
            showStatus("✗ Rank 1st, 2nd, and 3rd choices in at least one time block (or click \"Auto-Fill Unranked\") before submitting.", true);
            return;
        }
        if (!incompleteBlocks.isEmpty()) {
            showStatus("✗ Please finish ranking all 3 choices (1st, 2nd, and 3rd) in every time block you started: "
                    + String.join(", ", incompleteBlocks), true);
            return;
        }

        saveBallotToSession();
        Long tripId = trip.id();
        Long voterId = AppSession.getActiveUserId();
        String voterName = AppSession.getActiveTraveler().shortName();

        setBusy(true, "Submitting Borda Count rankings for " + voterName + "...");
        submitVotesBtn.setDisable(true);

        Task<SubmitOutcome> task = new Task<>() {
            @Override
            protected SubmitOutcome call() {
                ApiResult<List<VoteWinner>> submitRes = voteService.submitVotes(tripId, voterId, rankings);
                if (!submitRes.success()) {
                    return new SubmitOutcome(submitRes, null, null);
                }
                ApiResult<List<VoteWinner>> winnersRes = voteService.loadWinners(tripId);
                ApiResult<Itinerary> refreshedItinerary = itineraryService.loadItinerary(tripId);
                return new SubmitOutcome(submitRes, winnersRes, refreshedItinerary);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            submitVotesBtn.setDisable(false);
            SubmitOutcome outcome = task.getValue();

            if (!outcome.submitResult().success()) {
                showStatus("✗ " + outcome.submitResult().message(), true);
                return;
            }

            if (outcome.winnersResult() != null && outcome.winnersResult().success()
                    && outcome.winnersResult().data() != null && !outcome.winnersResult().data().isEmpty()) {
                this.apiWinners = outcome.winnersResult().data();
            } else if (outcome.submitResult().data() != null && !outcome.submitResult().data().isEmpty()) {
                this.apiWinners = outcome.submitResult().data();
            }

            if (outcome.itineraryResult() != null && outcome.itineraryResult().success()
                    && outcome.itineraryResult().data() != null) {
                this.itinerary = outcome.itineraryResult().data();
            }

            syncWinningOptions();
            renderWinnersSection();
            renderVotingBlocks();
            showStatus("✓ Votes submitted for " + voterName + "! Borda Count winners updated.", false);
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            submitVotesBtn.setDisable(false);
            showStatus("✗ Failed to submit votes: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "voting-submit");
    }

    private record SubmitOutcome(
            ApiResult<List<VoteWinner>> submitResult,
            ApiResult<List<VoteWinner>> winnersResult,
            ApiResult<Itinerary> itineraryResult
    ) {}

    @FXML
    public void handleRefreshWinners(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();
        setBusy(true, "Refreshing Borda Count winners...");

        Task<SubmitOutcome> task = new Task<>() {
            @Override
            protected SubmitOutcome call() {
                ApiResult<List<VoteWinner>> winnersRes = voteService.loadWinners(tripId);
                ApiResult<Itinerary> itRes = itineraryService.loadItinerary(tripId);
                return new SubmitOutcome(winnersRes, winnersRes, itRes);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            SubmitOutcome outcome = task.getValue();
            if (outcome.winnersResult() != null && outcome.winnersResult().success() && outcome.winnersResult().data() != null) {
                this.apiWinners = outcome.winnersResult().data();
            }
            if (outcome.itineraryResult() != null && outcome.itineraryResult().success() && outcome.itineraryResult().data() != null) {
                this.itinerary = outcome.itineraryResult().data();
            }
            syncWinningOptions();
            renderWinnersSection();
            renderVotingBlocks();
            showStatus("✓ Winners refreshed.", false);
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("✗ Couldn't refresh winners: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "voting-refresh-winners");
    }

    // ---------- Winners Calculation & Rendering ----------

    /**
     * Collects winning option IDs from:
     * 1) GET /api/trips/{id}/votes response (apiWinners)
     * 2) Options with is_final = true in GET /api/trips/{id}/itinerary (set by Lukas's VOTE-04 tally)
     * 3) Local session Borda tally fallback if votes were cast in this session and backend hasn't flagged winners yet.
     */
    private void syncWinningOptions() {
        winningOptionIds.clear();
        winningOptionPoints.clear();

        if (apiWinners != null) {
            for (VoteWinner w : apiWinners) {
                if (w.optionId() != null) {
                    winningOptionIds.add(w.optionId());
                    if (w.totalPoints() != null) {
                        winningOptionPoints.put(w.optionId(), w.totalPoints());
                    }
                }
            }
        }

        if (itinerary != null && itinerary.days() != null) {
            for (Itinerary.Day day : itinerary.days()) {
                for (Itinerary.Block block : orEmpty(day.blocks())) {
                    for (Itinerary.Subgroup sub : orEmpty(block.subgroups())) {
                        for (Itinerary.Option opt : orEmpty(sub.options())) {
                            if (opt.isFinal() && opt.id() != null) {
                                winningOptionIds.add(opt.id());
                            }
                        }
                    }
                }
            }
        }

        // Compute session Borda points so we can display point totals (and fallback winners if needed)
        if (trip != null && itinerary != null) {
            Map<Long, Map<Long, Integer>> tripBallots = SESSION_BALLOTS.getOrDefault(trip.id(), Map.of());
            if (!tripBallots.isEmpty()) {
                Map<Long, Integer> tally = new HashMap<>();
                for (Map<Long, Integer> userBallot : tripBallots.values()) {
                    for (Map.Entry<Long, Integer> vote : userBallot.entrySet()) {
                        int pts = Math.max(1, 4 - vote.getValue());
                        tally.merge(vote.getKey(), pts, Integer::sum);
                    }
                }
                tally.forEach(winningOptionPoints::putIfAbsent);

                if (winningOptionIds.isEmpty()) {
                    // Fallback Borda winner per subgroup block: highest points, tie-broken by lowest estimated_cost_usd
                    for (Itinerary.Day day : orEmpty(itinerary.days())) {
                        for (Itinerary.Block block : orEmpty(day.blocks())) {
                            for (Itinerary.Subgroup sub : orEmpty(block.subgroups())) {
                                Itinerary.Option best = null;
                                int bestPts = 0;
                                for (Itinerary.Option opt : orEmpty(sub.options())) {
                                    if (opt.id() == null) continue;
                                    int pts = tally.getOrDefault(opt.id(), 0);
                                    if (pts <= 0) continue;
                                    if (best == null || pts > bestPts
                                            || (pts == bestPts && compareCost(opt, best) < 0)) {
                                        best = opt;
                                        bestPts = pts;
                                    }
                                }
                                if (best != null && best.id() != null) {
                                    winningOptionIds.add(best.id());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int compareCost(Itinerary.Option a, Itinerary.Option b) {
        BigDecimal ca = a.estimatedCostUsd() == null ? BigDecimal.ZERO : a.estimatedCostUsd();
        BigDecimal cb = b.estimatedCostUsd() == null ? BigDecimal.ZERO : b.estimatedCostUsd();
        return ca.compareTo(cb);
    }

    private void renderWinnersSection() {
        winnersListBox.getChildren().clear();
        if (winningOptionIds.isEmpty() || itinerary == null) {
            winnersCard.setVisible(false);
            winnersCard.setManaged(false);
            return;
        }

        winnersCard.setVisible(true);
        winnersCard.setManaged(true);

        for (Itinerary.Day day : orEmpty(itinerary.days())) {
            for (Itinerary.Block block : orEmpty(day.blocks())) {
                for (Itinerary.Subgroup sub : orEmpty(block.subgroups())) {
                    for (Itinerary.Option opt : orEmpty(sub.options())) {
                        if (opt.id() != null && winningOptionIds.contains(opt.id())) {
                            winnersListBox.getChildren().add(winnerSummaryRow(day.date(), block.timeBlock(), sub, opt));
                        }
                    }
                }
            }
        }

        // If apiWinners had entries not matched in itinerary options, render them too
        if (winnersListBox.getChildren().isEmpty() && apiWinners != null) {
            for (VoteWinner w : apiWinners) {
                winnersListBox.getChildren().add(winnerFallbackRow(w));
            }
        }
    }

    private HBox winnerSummaryRow(String isoDate, String timeBlock, Itinerary.Subgroup sub, Itinerary.Option opt) {
        String dayBlock = shortDate(isoDate) + " · " + BLOCK_TITLES.getOrDefault(timeBlock, timeBlock);
        Label blockBadge = new Label(dayBlock);
        blockBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 8;");
        blockBadge.setMinWidth(185);

        Label titleLabel = new Label("🏆 " + opt.title());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");

        String details = timeRange(opt.startTime(), opt.endTime()) + " · " + costText(opt.estimatedCostUsd());
        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, blockBadge, titleLabel, detailsLabel, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

        Integer pts = winningOptionPoints.get(opt.id());
        if (pts != null && pts > 0) {
            Label ptsBadge = new Label(pts + (pts == 1 ? " Borda pt" : " Borda pts"));
            ptsBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 10;");
            row.getChildren().add(ptsBadge);
        }

        row.setStyle("-fx-background-color: white; -fx-border-color: #fde68a; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        return row;
    }

    private HBox winnerFallbackRow(VoteWinner w) {
        String dayBlock = (w.date() != null ? shortDate(w.date()) + " · " : "")
                + BLOCK_TITLES.getOrDefault(w.timeBlock() == null ? "" : w.timeBlock(),
                w.timeBlock() == null ? "Time Block" : w.timeBlock());
        Label blockBadge = new Label(dayBlock);
        blockBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 8;");

        Label titleLabel = new Label("🏆 " + (w.title() != null ? w.title() : "Option #" + w.optionId()));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, blockBadge, titleLabel, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        if (w.totalPoints() != null) {
            Label ptsBadge = new Label(w.totalPoints() + " Borda pts");
            ptsBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 10;");
            row.getChildren().add(ptsBadge);
        }
        row.setStyle("-fx-background-color: white; -fx-border-color: #fde68a; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        return row;
    }

    // ---------- Rendering Voting Blocks ----------

    private void renderVotingBlocks() {
        daysVotingBox.getChildren().clear();
        blockOptionsByKey.clear();

        if (itinerary == null || itinerary.days() == null || itinerary.days().isEmpty()) {
            showEmptyState();
            return;
        }

        emptyStateBox.setVisible(false);
        emptyStateBox.setManaged(false);
        setControlsDisabled(false);

        Long voterId = AppSession.getActiveUserId();
        List<Itinerary.Day> days = itinerary.days();
        for (int d = 0; d < days.size(); d++) {
            daysVotingBox.getChildren().add(dayVotingCard(d + 1, days.get(d), voterId));
        }

        updateProgressLabel();
    }

    private VBox dayVotingCard(int dayNumber, Itinerary.Day day, Long voterId) {
        Label badge = new Label("DAY " + dayNumber);
        badge.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 12;");
        Label date = new Label(niceDate(day.date()));
        date.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #0f172a;");
        HBox header = new HBox(12, badge, date);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, header);
        card.setStyle(DAY_CARD_STYLE);

        for (Itinerary.Block block : orEmpty(day.blocks())) {
            card.getChildren().add(blockVotingSection(dayNumber, day.date(), block, voterId));
        }
        return card;
    }

    private VBox blockVotingSection(int dayNumber, String isoDate, Itinerary.Block block, Long voterId) {
        VBox section = new VBox(10);

        List<Itinerary.Subgroup> allSubgroups = orEmpty(block.subgroups());
        // Show the subgroup that contains the active voter (or all subgroups if none specify this voter)
        List<Itinerary.Subgroup> matching = allSubgroups.stream()
                .filter(s -> s.subgroupParticipants() == null
                        || s.subgroupParticipants().isEmpty()
                        || s.subgroupParticipants().contains(voterId))
                .toList();
        if (matching.isEmpty()) {
            matching = allSubgroups;
        }

        boolean split = allSubgroups.size() > 1;
        for (int sIdx = 0; sIdx < matching.size(); sIdx++) {
            Itinerary.Subgroup sub = matching.get(sIdx);
            List<Itinerary.Option> opts = orEmpty(sub.options());
            String blockKey = "Day " + dayNumber + " " + capitalize(block.timeBlock())
                    + (matching.size() > 1 ? " (Group " + (sIdx + 1) + ")" : "");
            blockOptionsByKey.put(blockKey, opts);

            // Header bar for the time block + completion badge
            Label blockTitle = new Label(BLOCK_TITLES.getOrDefault(block.timeBlock(), block.timeBlock()));
            blockTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");

            int expected = Math.min(3, opts.size());
            long rankedCount = opts.stream()
                    .filter(o -> o.id() != null && currentRanks.containsKey(o.id()))
                    .count();
            boolean complete = expected > 0 && rankedCount == expected;

            Label statusPill = new Label(complete
                    ? "✓ Ranked 1st, 2nd & 3rd"
                    : "Rank your 1st, 2nd & 3rd choice (" + rankedCount + "/" + expected + ")");
            statusPill.setStyle(complete
                    ? "-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 10;"
                    : "-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 10;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox titleRow = new HBox(10, blockTitle, spacer, statusPill);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            section.getChildren().add(titleRow);

            if (split) {
                section.getChildren().add(subgroupLabel(sub));
            }

            HBox optionsRow = new HBox(12);
            for (int i = 0; i < opts.size(); i++) {
                VBox optCard = votingOptionCard(blockKey, i + 1, opts.get(i));
                HBox.setHgrow(optCard, Priority.ALWAYS);
                optCard.setPrefWidth(0);
                optCard.setMaxWidth(Double.MAX_VALUE);
                optionsRow.getChildren().add(optCard);
            }
            section.getChildren().add(optionsRow);
        }

        return section;
    }

    private Label subgroupLabel(Itinerary.Subgroup sub) {
        List<Long> people = orEmpty(sub.subgroupParticipants());
        String who = people.stream()
                .map(id -> {
                    Participant p = participantById.get(id);
                    return p != null ? p.shortName() : "Traveler " + id;
                })
                .collect(Collectors.joining(", "));
        String reason = (sub.splitReason() == null || sub.splitReason().isBlank()) ? "" : " · " + sub.splitReason();
        Label label = new Label("👥 Subgroup (" + who + ")" + reason);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #7c3aed; -fx-font-weight: bold; -fx-font-size: 12px;");
        return label;
    }

    private VBox votingOptionCard(String blockKey, int number, Itinerary.Option option) {
        Integer selectedRank = option.id() == null ? null : currentRanks.get(option.id());
        boolean isWinner = option.isFinal() || (option.id() != null && winningOptionIds.contains(option.id()));

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label pick = new Label("OPTION " + number);
        pick.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 10px;");
        topRow.getChildren().add(pick);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topRow.getChildren().add(topSpacer);

        if (isWinner) {
            Integer pts = option.id() == null ? null : winningOptionPoints.get(option.id());
            String winText = pts != null && pts > 0 ? "🏆 WINNER (" + pts + " pts)" : "🏆 WINNER";
            Label winBadge = new Label(winText);
            winBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 8;");
            topRow.getChildren().add(winBadge);
        }

        Label title = wrapped(option.title(), "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
        Label time = wrapped("🕘 " + timeRange(option.startTime(), option.endTime()), "-fx-text-fill: #334155; -fx-font-size: 12px;");

        VBox card = new VBox(6, topRow, title, time);
        if (option.neighborhood() != null && !option.neighborhood().isBlank()) {
            card.getChildren().add(wrapped("📍 " + option.neighborhood(), "-fx-text-fill: #334155; -fx-font-size: 12px;"));
        }

        HBox chips = new HBox(6, categoryChip(option.category()), costChip(option.estimatedCostUsd()));
        chips.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(chips);

        if (option.description() != null && !option.description().isBlank()) {
            card.getChildren().add(wrapped(option.description(), "-fx-text-fill: #475569; -fx-font-size: 12px;"));
        }

        Region pushDown = new Region();
        VBox.setVgrow(pushDown, Priority.ALWAYS);
        card.getChildren().add(pushDown);

        // Rank 1, 2, 3 Buttons
        HBox rankBar = new HBox(6);
        rankBar.setAlignment(Pos.CENTER);
        rankBar.getChildren().addAll(
                rankButton(blockKey, option, 1, "🥇 1st (3pt)", selectedRank != null && selectedRank == 1),
                rankButton(blockKey, option, 2, "🥈 2nd (2pt)", selectedRank != null && selectedRank == 2),
                rankButton(blockKey, option, 3, "🥉 3rd (1pt)", selectedRank != null && selectedRank == 3)
        );
        card.getChildren().add(rankBar);

        if (isWinner) {
            card.setStyle("-fx-background-color: #fffbeb; -fx-border-color: #f59e0b; -fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");
        } else if (selectedRank != null && selectedRank == 1) {
            card.setStyle("-fx-background-color: #eff6ff; -fx-border-color: #2563eb; -fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");
        } else if (selectedRank != null) {
            card.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #93c5fd; -fx-border-width: 1.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");
        } else {
            card.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");
        }
        return card;
    }

    private Button rankButton(String blockKey, Itinerary.Option option, int rank, String text, boolean active) {
        Button btn = new Button(text);
        HBox.setHgrow(btn, Priority.ALWAYS);
        btn.setMaxWidth(Double.MAX_VALUE);

        String activeColors = switch (rank) {
            case 1 -> "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #1d4ed8;";
            case 2 -> "-fx-background-color: #0284c7; -fx-text-fill: white; -fx-border-color: #0369a1;";
            default -> "-fx-background-color: #475569; -fx-text-fill: white; -fx-border-color: #334155;";
        };
        String inactiveColors = "-fx-background-color: white; -fx-text-fill: #334155; -fx-border-color: #cbd5e1;";

        btn.setStyle((active ? activeColors : inactiveColors)
                + " -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 6; -fx-background-radius: 6; -fx-border-radius: 6; -fx-cursor: hand;");
        btn.setOnAction(e -> assignRank(blockKey, option, rank));
        return btn;
    }

    private void updateProgressLabel() {
        int totalBlocks = blockOptionsByKey.size();
        int completedBlocks = 0;
        for (List<Itinerary.Option> opts : blockOptionsByKey.values()) {
            int expected = Math.min(3, opts.size());
            long ranked = opts.stream().filter(o -> o.id() != null && currentRanks.containsKey(o.id())).count();
            if (expected > 0 && ranked == expected) {
                completedBlocks++;
            }
        }
        progressLabel.setText(completedBlocks + " of " + totalBlocks + " time blocks ranked");
    }

    // ---------- Session Ballot Persistence ----------

    private void saveBallotToSession() {
        if (trip == null) return;
        SESSION_BALLOTS
                .computeIfAbsent(trip.id(), k -> new HashMap<>())
                .put(AppSession.getActiveUserId(), new LinkedHashMap<>(currentRanks));
    }

    private void loadBallotForActiveVoter() {
        currentRanks.clear();
        if (trip == null) return;
        Map<Long, Integer> saved = SESSION_BALLOTS
                .getOrDefault(trip.id(), Map.of())
                .get(AppSession.getActiveUserId());
        if (saved != null) {
            currentRanks.putAll(saved);
        }
    }

    // ---------- UI & Navigation Helpers ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText("Vote on Options: " + t.title());
        tripDetailsLabel.setText("📍 " + t.destination()
                + "   📅 " + t.dateRangeText()
                + "   🧳 " + TripType.labelFor(t.tripType())
                + "   💰 " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyState() {
        daysVotingBox.getChildren().clear();
        winnersCard.setVisible(false);
        winnersCard.setManaged(false);
        emptyStateBox.setVisible(true);
        emptyStateBox.setManaged(true);
        setControlsDisabled(true);
    }

    private void setControlsDisabled(boolean disabled) {
        autoRankBtn.setDisable(disabled);
        clearRanksBtn.setDisable(disabled);
        submitVotesBtn.setDisable(disabled);
    }

    @FXML
    public void goToSharedTrip(ActionEvent event) {
        saveBallotToSession();
        NavigationManager.getInstance().navigateTo("trips");
    }

    @FXML
    public void goToItinerary(ActionEvent event) {
        saveBallotToSession();
        NavigationManager.getInstance().navigateTo("itinerary");
    }

    private Label categoryChip(String category) {
        String name = category == null || category.isBlank() ? "other" : category;
        String[] colors = CATEGORY_COLORS.getOrDefault(name, new String[]{"#f1f5f9", "#334155"});
        Label chip = new Label(capitalize(name));
        chip.setStyle("-fx-background-color: " + colors[0] + "; -fx-text-fill: " + colors[1]
                + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 10;");
        return chip;
    }

    private Label costChip(BigDecimal cost) {
        Label chip = new Label(costText(cost));
        chip.setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #047857; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 10;");
        return chip;
    }

    private static String costText(BigDecimal cost) {
        if (cost == null) return "Cost unknown";
        if (cost.signum() == 0) return "Free";
        return money(cost) + " / person";
    }

    private static Label wrapped(String text, String style) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle(style);
        return label;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String timeRange(String start, String end) {
        String from = twelveHour(start);
        String to = twelveHour(end);
        if (from == null && to == null) return "Time to be decided";
        if (to == null) return "Starts " + from;
        if (from == null) return "Until " + to;
        return from + " to " + to;
    }

    private static String twelveHour(String hhmm) {
        if (hhmm == null) return null;
        try {
            return LocalTime.parse(hhmm).format(TWELVE_HOUR);
        } catch (RuntimeException e) {
            return hhmm;
        }
    }

    private static String money(BigDecimal amount) {
        if (amount == null) return "$?";
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        format.setMinimumFractionDigits(whole ? 0 : 2);
        format.setMaximumFractionDigits(whole ? 0 : 2);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static String niceDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(DAY_HEADER);
        } catch (RuntimeException e) {
            return Objects.toString(isoDate, "");
        }
    }

    private static String shortDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(SHORT_DATE);
        } catch (RuntimeException e) {
            return Objects.toString(isoDate, "");
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private void setBusy(boolean busy, String message) {
        busyBox.setVisible(busy);
        busyBox.setManaged(busy);
        busyLabel.setText(message == null ? "" : message);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;"
                : "-fx-text-fill: #059669; -fx-font-weight: bold;");
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private static void startInBackground(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
