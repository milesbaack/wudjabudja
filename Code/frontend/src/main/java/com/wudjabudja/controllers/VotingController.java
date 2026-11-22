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
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
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
 * Handles traveler ballot switching, unique 1st/2nd/3rd Borda ranking per time block,
 * vote submission, and Borda Count winners display.
 */
public class VotingController implements Initializable {

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "morning", "Morning",
            "afternoon", "Afternoon",
            "evening", "Evening"
    );

    private static final Map<Long, Map<Long, Map<Long, Integer>>> SESSION_BALLOTS = new HashMap<>();

    public static void clearVoterBallot(Long tripId, Long userId) {
        if (tripId == null || userId == null) return;
        Map<Long, Map<Long, Integer>> tripBallots = SESSION_BALLOTS.get(tripId);
        if (tripBallots != null) {
            tripBallots.remove(userId);
        }
    }

    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private ComboBox<Participant> voterCombo;
    @FXML private Label progressLabel;
    @FXML private Button autoRankBtn;
    @FXML private Button clearRanksBtn;
    @FXML private Button submitVotesBtn;
    @FXML private Button refreshWinnersBtn;
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

    private final Map<Long, Integer> currentRanks = new LinkedHashMap<>();
    private final Map<String, List<Itinerary.Option>> blockOptionsByKey = new LinkedHashMap<>();
    private boolean updatingVoterCombo = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (submitVotesBtn != null) {
            submitVotesBtn.setGraphic(Icons.create(Icons.CHECK, 15, "#FFFFFF"));
        }
        if (refreshWinnersBtn != null) {
            refreshWinnersBtn.setGraphic(Icons.create(Icons.REFRESH, 13, "#334155"));
        }

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
                NavigationManager.getInstance().refreshSidebar();
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
                showStatus(itRes.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("Failed to load voting data: " + task.getException().getMessage(), true);
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

    // ---------- Ranking Logic ----------

    private void assignRank(String blockKey, Itinerary.Option targetOption, int newRank) {
        if (targetOption.id() == null) {
            showStatus("Option is missing a database ID. Regenerate the itinerary first.", true);
            return;
        }
        List<Itinerary.Option> siblingOptions = blockOptionsByKey.getOrDefault(blockKey, List.of());
        Integer previousRankOnTarget = currentRanks.get(targetOption.id());

        if (previousRankOnTarget != null && previousRankOnTarget == newRank) {
            currentRanks.remove(targetOption.id());
        } else {
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
        showStatus("Auto-ranked all time blocks (1st, 2nd, 3rd) for " + AppSession.getActiveTraveler().shortName()
                + ". Click \"Submit Votes\" when ready.", false);
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
            showStatus("Rank 1st, 2nd, and 3rd choices in at least one time block (or click \"Auto-Fill Unranked\") before submitting.", true);
            return;
        }
        if (!incompleteBlocks.isEmpty()) {
            showStatus("Please finish ranking all 3 choices (1st, 2nd, and 3rd) in every time block you started: "
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
                showStatus(outcome.submitResult().message(), true);
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
            showStatus("Votes submitted for " + voterName + ". Borda Count winners updated.", false);
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            submitVotesBtn.setDisable(false);
            showStatus("Failed to submit votes: " + task.getException().getMessage(), true);
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
            showStatus("Winners refreshed.", false);
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("Couldn't refresh winners: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "voting-refresh-winners");
    }

    // ---------- Winners Calculation & Rendering ----------

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

        if (trip != null && itinerary != null) {
            Map<Long, Map<Long, Integer>> tripBallots = SESSION_BALLOTS.getOrDefault(trip.id(), Map.of());
            if (!tripBallots.isEmpty()) {
                Map<Long, Integer> tally = new HashMap<>();
                for (Map.Entry<Long, Map<Long, Integer>> voterEntry : tripBallots.entrySet()) {
                    if (!participantById.isEmpty() && !participantById.containsKey(voterEntry.getKey())) {
                        continue;
                    }
                    for (Map.Entry<Long, Integer> vote : voterEntry.getValue().entrySet()) {
                        int pts = Math.max(1, 4 - vote.getValue());
                        tally.merge(vote.getKey(), pts, Integer::sum);
                    }
                }
                tally.forEach(winningOptionPoints::putIfAbsent);

                if (winningOptionIds.isEmpty()) {
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

        if (winnersListBox.getChildren().isEmpty() && apiWinners != null) {
            for (VoteWinner w : apiWinners) {
                winnersListBox.getChildren().add(winnerFallbackRow(w));
            }
        }
    }

    private HBox winnerSummaryRow(String isoDate, String timeBlock, Itinerary.Subgroup sub, Itinerary.Option opt) {
        String dayBlock = shortDate(isoDate) + " · " + BLOCK_TITLES.getOrDefault(timeBlock, timeBlock);
        Label blockBadge = new Label(dayBlock);
        blockBadge.getStyleClass().addAll("badge", "badge-amber");
        blockBadge.setMinWidth(185);

        Label titleLabel = new Label(opt.title());
        titleLabel.getStyleClass().add("item-title");

        String details = timeRange(opt.startTime(), opt.endTime()) + " · " + costText(opt.estimatedCostUsd());
        Label detailsLabel = new Label(details);
        detailsLabel.getStyleClass().add("meta-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, blockBadge, titleLabel, detailsLabel, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

        Integer pts = winningOptionPoints.get(opt.id());
        if (pts != null && pts > 0) {
            Label ptsBadge = new Label(pts + (pts == 1 ? " Borda pt" : " Borda pts"));
            ptsBadge.getStyleClass().addAll("badge", "badge-success");
            row.getChildren().add(ptsBadge);
        }

        row.getStyleClass().add("winner-summary-row");
        return row;
    }

    private HBox winnerFallbackRow(VoteWinner w) {
        String dayBlock = (w.date() != null ? shortDate(w.date()) + " · " : "")
                + BLOCK_TITLES.getOrDefault(w.timeBlock() == null ? "" : w.timeBlock(),
                w.timeBlock() == null ? "Time Block" : w.timeBlock());
        Label blockBadge = new Label(dayBlock);
        blockBadge.getStyleClass().addAll("badge", "badge-amber");

        Label titleLabel = new Label(w.title() != null ? w.title() : "Option #" + w.optionId());
        titleLabel.getStyleClass().add("item-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, blockBadge, titleLabel, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        if (w.totalPoints() != null) {
            Label ptsBadge = new Label(w.totalPoints() + " Borda pts");
            ptsBadge.getStyleClass().addAll("badge", "badge-success");
            row.getChildren().add(ptsBadge);
        }
        row.getStyleClass().add("winner-summary-row");
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
        badge.getStyleClass().addAll("badge", "badge-dark");
        Label date = new Label(niceDate(day.date()));
        date.getStyleClass().add("section-title");
        HBox header = new HBox(12, badge, date);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, header);
        card.getStyleClass().add("card");

        for (Itinerary.Block block : orEmpty(day.blocks())) {
            card.getChildren().add(blockVotingSection(dayNumber, day.date(), block, voterId));
        }
        return card;
    }

    private VBox blockVotingSection(int dayNumber, String isoDate, Itinerary.Block block, Long voterId) {
        VBox section = new VBox(10);

        List<Itinerary.Subgroup> allSubgroups = orEmpty(block.subgroups());
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

            Label blockTitle = new Label(BLOCK_TITLES.getOrDefault(block.timeBlock(), block.timeBlock()));
            blockTitle.getStyleClass().add("card-title");

            int expected = Math.min(3, opts.size());
            long rankedCount = opts.stream()
                    .filter(o -> o.id() != null && currentRanks.containsKey(o.id()))
                    .count();
            boolean complete = expected > 0 && rankedCount == expected;

            Label statusPill = new Label(complete
                    ? "Ranked 1st, 2nd & 3rd"
                    : "Rank 1st, 2nd & 3rd (" + rankedCount + "/" + expected + ")");
            statusPill.getStyleClass().addAll("badge", complete ? "badge-success" : "badge-neutral");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox titleRow = new HBox(10, blockTitle, spacer, statusPill);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            section.getChildren().add(titleRow);

            if (split) {
                section.getChildren().add(subgroupLabel(sub));
            }

            HBox optionsRow = new HBox(14);
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
        Label label = new Label("Subgroup (" + who + ")" + reason);
        label.setWrapText(true);
        label.getStyleClass().addAll("badge", "badge-violet");
        return label;
    }

    private VBox votingOptionCard(String blockKey, int number, Itinerary.Option option) {
        Integer selectedRank = option.id() == null ? null : currentRanks.get(option.id());
        boolean isWinner = option.isFinal() || (option.id() != null && winningOptionIds.contains(option.id()));

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label pick = new Label("OPTION " + number);
        pick.getStyleClass().add("subtle-text");
        pick.setStyle("-fx-font-weight: bold;");
        topRow.getChildren().add(pick);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topRow.getChildren().add(topSpacer);

        if (isWinner) {
            Integer pts = option.id() == null ? null : winningOptionPoints.get(option.id());
            String winText = pts != null && pts > 0 ? "WINNER · " + pts + " PTS" : "WINNER";
            Label winBadge = new Label(winText);
            winBadge.getStyleClass().addAll("badge", "badge-amber");
            topRow.getChildren().add(winBadge);
        }

        Label title = wrapped(option.title(), "item-title");
        HBox timeRow = new HBox(6, Icons.create(Icons.CLOCK, 13, "#64748B"),
                wrapped(timeRange(option.startTime(), option.endTime()), "meta-text"));
        timeRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, topRow, title, timeRow);
        if (option.neighborhood() != null && !option.neighborhood().isBlank()) {
            HBox locRow = new HBox(6, Icons.create(Icons.MAP_PIN, 13, "#64748B"),
                    wrapped(option.neighborhood(), "meta-text"));
            locRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(locRow);
        }

        HBox chips = new HBox(6, UiTheme.categoryChip(option.category()), UiTheme.costChip(costText(option.estimatedCostUsd())));
        chips.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(chips);

        if (option.description() != null && !option.description().isBlank()) {
            card.getChildren().add(wrapped(option.description(), "body-text"));
        }

        Region pushDown = new Region();
        VBox.setVgrow(pushDown, Priority.ALWAYS);
        card.getChildren().add(pushDown);

        Region divider = new Region();
        divider.getStyleClass().add("divider");
        card.getChildren().add(divider);

        HBox rankBar = new HBox(6);
        rankBar.setAlignment(Pos.CENTER);
        rankBar.getChildren().addAll(
                rankButton(blockKey, option, 1, "1st · 3pt", selectedRank != null && selectedRank == 1),
                rankButton(blockKey, option, 2, "2nd · 2pt", selectedRank != null && selectedRank == 2),
                rankButton(blockKey, option, 3, "3rd · 1pt", selectedRank != null && selectedRank == 3)
        );
        card.getChildren().add(rankBar);

        if (isWinner) {
            card.getStyleClass().add("option-card-winner");
        } else if (selectedRank != null && selectedRank == 1) {
            card.getStyleClass().add("option-card-rank1");
        } else if (selectedRank != null) {
            card.getStyleClass().add("option-card-ranked");
        } else {
            card.getStyleClass().add("option-card");
        }
        return card;
    }

    private Button rankButton(String blockKey, Itinerary.Option option, int rank, String text, boolean active) {
        Button btn = new Button(text);
        HBox.setHgrow(btn, Priority.ALWAYS);
        btn.setMaxWidth(Double.MAX_VALUE);

        if (active) {
            btn.getStyleClass().add(switch (rank) {
                case 1 -> "rank-btn-1";
                case 2 -> "rank-btn-2";
                default -> "rank-btn-3";
            });
        } else {
            btn.getStyleClass().add("rank-btn");
        }

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
        progressLabel.setText(completedBlocks + " of " + totalBlocks + " blocks ranked");
        UiTheme.setBadgeVariant(progressLabel, (totalBlocks > 0 && completedBlocks == totalBlocks) ? "badge-success" : "badge-neutral");
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
        tripTitleLabel.setText("Vote on Options — " + t.title());
        tripDetailsLabel.setText(t.destination()
                + "  ·  " + t.dateRangeText()
                + "  ·  " + TripType.labelFor(t.tripType())
                + "  ·  " + money(t.totalBudget()) + " total budget");
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

    private static String costText(BigDecimal cost) {
        if (cost == null) return "Cost unknown";
        if (cost.signum() == 0) return "Free";
        return money(cost) + " / person";
    }

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
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
        return from + " – " + to;
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
        UiTheme.showStatus(statusLabel, message, isError);
    }

    private void clearStatus() {
        UiTheme.clearStatus(statusLabel);
    }

    private static void startInBackground(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
