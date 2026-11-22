package com.wudjabudja.controllers;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripType;
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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
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
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Itinerary screen (MVP-09 & FINAL-01).
 * Loads the saved itinerary for the current trip, supports local filtering and sorting,
 * and displays a consensus budget roll-up banner once Borda Count winners are elected.
 */
public class ItineraryController implements Initializable {

    private static Task<ApiResult<Itinerary>> runningTask;
    private static Long runningTripId;
    private static Instant runningSince;

    private static final String GEMINI_WAIT_TEXT = "Planning your trip, this can take a minute or two...";

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final String ALL_CATEGORIES = "All Categories";
    private static final String ALL_TRAVELERS = "All Travelers";
    private static final String SORT_DEFAULT = "Default Order";
    private static final String SORT_CHEAPEST = "Lowest Cost First";
    private static final String SORT_PRICIEST = "Highest Cost First";
    private static final String SORT_EARLIEST = "Earliest Start Time";

    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "morning", "Morning", "afternoon", "Afternoon", "evening", "Evening");

    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private Label generatedLabel;
    @FXML private Button generateBtn;
    @FXML private Button editPrefsBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private Label statusLabel;

    @FXML private HBox consensusBudgetCard;
    @FXML private Label consensusTitleLabel;
    @FXML private Label consensusCostLabel;
    @FXML private Label consensusBudgetBadge;

    @FXML private VBox filterBarCard;
    @FXML private Button allOptionsModeBtn;
    @FXML private Button winnersOnlyModeBtn;
    @FXML private Label filterSummaryLabel;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private ComboBox<String> travelerFilterCombo;
    @FXML private ComboBox<String> sortCombo;

    @FXML private VBox emptyStateBox;
    @FXML private VBox daysBox;

    private final TripService tripService = new TripService();
    private final ItineraryService itineraryService = new ItineraryService();
    private final ParticipantService participantService = new ParticipantService();
    private final VoteService voteService = new VoteService();

    private Trip trip;
    private Itinerary currentItinerary;
    private final Map<Long, Participant> participantById = new HashMap<>();
    private final Map<String, Long> travelerIdByFilterLabel = new HashMap<>();
    private final Set<Long> winningOptionIds = new HashSet<>();
    private boolean winnersOnlyMode = false;
    private boolean suppressFilterEvents = false;
    private Timeline clock;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (generateBtn != null) {
            generateBtn.setGraphic(Icons.create(Icons.SPARKLES, 15, "#FFFFFF"));
        }

        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create one first.", true);
            generateBtn.setDisable(true);
            return;
        }
        setupFilterControls();
        showTripHeader(trip);

        if (runningTask != null && trip.id().equals(runningTripId) && !runningTask.isDone()) {
            watchGeneration(runningTask);
        } else {
            loadSaved();
        }
    }

    private void setupFilterControls() {
        suppressFilterEvents = true;
        categoryFilterCombo.getItems().setAll(
                ALL_CATEGORIES, "Food", "Outdoors", "Culture", "Nightlife", "Sports", "Shopping", "Relaxation", "Tour"
        );
        categoryFilterCombo.setValue(ALL_CATEGORIES);

        travelerFilterCombo.getItems().setAll(ALL_TRAVELERS);
        travelerFilterCombo.setValue(ALL_TRAVELERS);

        sortCombo.getItems().setAll(SORT_DEFAULT, SORT_CHEAPEST, SORT_PRICIEST, SORT_EARLIEST);
        sortCombo.setValue(SORT_DEFAULT);
        suppressFilterEvents = false;
    }

    // ---------- loading what's already saved ----------

    private record LoadOutcome(
            ApiResult<Trip> trip,
            ApiResult<List<Participant>> participants,
            ApiResult<Itinerary> itinerary,
            ApiResult<List<VoteWinner>> winners
    ) {}

    private void loadSaved() {
        setBusy(true, "Loading your saved itinerary...");
        Long tripId = trip.id();
        Task<LoadOutcome> task = new Task<>() {
            @Override
            protected LoadOutcome call() {
                ApiResult<Trip> tripResult = tripService.getTrip(tripId);
                if (!tripResult.success()) return new LoadOutcome(tripResult, null, null, null);
                ApiResult<List<Participant>> partResult = participantService.listParticipants(tripId);
                ApiResult<Itinerary> itResult = itineraryService.loadItinerary(tripId);
                ApiResult<List<VoteWinner>> winResult = voteService.loadWinners(tripId);
                return new LoadOutcome(tripResult, partResult, itResult, winResult);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false, null);
            showLoadOutcome(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("Couldn't load the itinerary: " + task.getException().getMessage(), true);
        });
        startInBackground(task, "itinerary-load");
    }

    private void showLoadOutcome(LoadOutcome outcome) {
        ApiResult<Trip> tripResult = outcome.trip();
        if (!tripResult.success()) {
            if (tripResult.isNotFound()) {
                showStatus("Trip #" + trip.id() + " isn't in the database anymore. Use \"Plan a Trip\" to make a new one.", true);
                generateBtn.setDisable(true);
            } else {
                showStatus(tripResult.message(), true);
            }
            return;
        }

        trip = tripResult.data();
        AppSession.setCurrentTrip(trip);
        NavigationManager.getInstance().refreshSidebar();
        showTripHeader(trip);

        participantById.clear();
        travelerIdByFilterLabel.clear();
        suppressFilterEvents = true;
        travelerFilterCombo.getItems().setAll(ALL_TRAVELERS);
        if (outcome.participants() != null && outcome.participants().success() && outcome.participants().data() != null) {
            for (Participant p : outcome.participants().data()) {
                if (p.userId() != null) {
                    participantById.put(p.userId(), p);
                    String label = p.shortName() + " (#" + p.userId() + ")";
                    travelerIdByFilterLabel.put(label, p.userId());
                    travelerFilterCombo.getItems().add(label);
                }
            }
        }
        travelerFilterCombo.setValue(ALL_TRAVELERS);
        suppressFilterEvents = false;

        winningOptionIds.clear();
        if (outcome.winners() != null && outcome.winners().success() && outcome.winners().data() != null) {
            for (VoteWinner w : outcome.winners().data()) {
                if (w.optionId() != null) {
                    winningOptionIds.add(w.optionId());
                }
            }
        }

        ApiResult<Itinerary> itineraryResult = outcome.itinerary();
        if (itineraryResult.success() && itineraryResult.data() != null) {
            this.currentItinerary = itineraryResult.data();
            collectFinalFlags(this.currentItinerary);
            showItinerary(this.currentItinerary);
        } else if (itineraryResult.isNotFound()) {
            this.currentItinerary = null;
            showEmptyState();
        } else {
            showStatus(itineraryResult.message(), true);
        }
    }

    private void collectFinalFlags(Itinerary itinerary) {
        if (itinerary == null || itinerary.days() == null) return;
        for (Itinerary.Day d : itinerary.days()) {
            if (d.blocks() == null) continue;
            for (Itinerary.Block b : d.blocks()) {
                if (b.subgroups() == null) continue;
                for (Itinerary.Subgroup s : b.subgroups()) {
                    if (s.options() == null) continue;
                    for (Itinerary.Option o : s.options()) {
                        if (o.isFinal() && o.id() != null) {
                            winningOptionIds.add(o.id());
                        }
                    }
                }
            }
        }
    }

    private boolean isWinningOption(Itinerary.Option option) {
        return option != null && (option.isFinal() || (option.id() != null && winningOptionIds.contains(option.id())));
    }

    // ---------- Local Filtering & Sorting Handlers ----------

    @FXML
    public void handleShowAllOptions(ActionEvent event) {
        winnersOnlyMode = false;
        updateViewModeButtons();
        if (currentItinerary != null) {
            showItinerary(currentItinerary);
        }
    }

    @FXML
    public void handleShowWinnersOnly(ActionEvent event) {
        if (winningOptionIds.isEmpty()) {
            showStatus("No Borda Count winners have been elected yet. Head to \"Vote on Options\" to rank choices first.", false);
            return;
        }
        clearStatus();
        winnersOnlyMode = true;
        updateViewModeButtons();
        if (currentItinerary != null) {
            showItinerary(currentItinerary);
        }
    }

    @FXML
    public void handleFilterOrSortChanged(ActionEvent event) {
        if (suppressFilterEvents || currentItinerary == null) return;
        showItinerary(currentItinerary);
    }

    @FXML
    public void handleResetFilters(ActionEvent event) {
        suppressFilterEvents = true;
        winnersOnlyMode = false;
        categoryFilterCombo.setValue(ALL_CATEGORIES);
        travelerFilterCombo.setValue(ALL_TRAVELERS);
        sortCombo.setValue(SORT_DEFAULT);
        suppressFilterEvents = false;
        updateViewModeButtons();
        if (currentItinerary != null) {
            showItinerary(currentItinerary);
        }
    }

    private void updateViewModeButtons() {
        allOptionsModeBtn.getStyleClass().removeAll("segment-btn", "segment-btn-active");
        winnersOnlyModeBtn.getStyleClass().removeAll("segment-btn", "segment-btn-active");
        allOptionsModeBtn.getStyleClass().add(winnersOnlyMode ? "segment-btn" : "segment-btn-active");
        winnersOnlyModeBtn.getStyleClass().add(winnersOnlyMode ? "segment-btn-active" : "segment-btn");
    }

    // ---------- generating ----------

    @FXML
    public void handleGenerate(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();

        Task<ApiResult<Itinerary>> task = new Task<>() {
            @Override
            protected ApiResult<Itinerary> call() {
                return itineraryService.generateItinerary(tripId);
            }
        };
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> forgetRunningTask(task));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> forgetRunningTask(task));

        runningTask = task;
        runningTripId = tripId;
        runningSince = Instant.now();

        clearStatus();
        watchGeneration(task);
        startInBackground(task, "itinerary-generate");
    }

    private void watchGeneration(Task<ApiResult<Itinerary>> task) {
        setGenerating(true);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            setGenerating(false);
            ApiResult<Itinerary> result = task.getValue();
            if (result.success() && result.data() != null) {
                this.currentItinerary = result.data();
                this.winningOptionIds.clear();
                collectFinalFlags(this.currentItinerary);
                this.winnersOnlyMode = false;
                updateViewModeButtons();
                showItinerary(this.currentItinerary);
                showStatus("Your itinerary is ready. Pick a favorite in each time block.", false);
            } else {
                showStatus(result.message(), true);
            }
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            setGenerating(false);
            showStatus("Something went wrong while generating: " + task.getException().getMessage(), true);
        });
    }

    private static void forgetRunningTask(Task<ApiResult<Itinerary>> task) {
        if (runningTask == task) {
            runningTask = null;
            runningTripId = null;
            runningSince = null;
        }
    }

    private void setGenerating(boolean generating) {
        generateBtn.setDisable(generating);
        editPrefsBtn.setDisable(generating);
        if (generating) {
            Instant since = runningSince == null ? Instant.now() : runningSince;
            setBusy(true, GEMINI_WAIT_TEXT);
            clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                long seconds = java.time.Duration.between(since, Instant.now()).toSeconds();
                busyLabel.setText(GEMINI_WAIT_TEXT + String.format(" (%d:%02d)", seconds / 60, seconds % 60));
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        } else {
            if (clock != null) clock.stop();
            setBusy(false, null);
        }
    }

    @FXML public void goToPreferences(ActionEvent event) { NavigationManager.getInstance().navigateTo("preferences"); }
    @FXML public void goToSharedTrip(ActionEvent event) { NavigationManager.getInstance().navigateTo("trips"); }
    @FXML public void goToVoting(ActionEvent event) { NavigationManager.getInstance().navigateTo("voting"); }

    // ---------- drawing the itinerary ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText(t.title());
        tripDetailsLabel.setText(t.destination()
                + "  ·  " + t.dateRangeText() + " (" + t.numDays() + (t.numDays() == 1 ? " day)" : " days)")
                + "  ·  " + TripType.labelFor(t.tripType())
                + "  ·  " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyState() {
        daysBox.getChildren().clear();
        filterBarCard.setVisible(false);
        filterBarCard.setManaged(false);
        consensusBudgetCard.setVisible(false);
        consensusBudgetCard.setManaged(false);
        emptyStateBox.setVisible(true);
        emptyStateBox.setManaged(true);
        generateBtn.setText("Generate Itinerary");
        generatedLabel.setText("");
    }

    private void showItinerary(Itinerary itinerary) {
        emptyStateBox.setVisible(false);
        emptyStateBox.setManaged(false);
        filterBarCard.setVisible(true);
        filterBarCard.setManaged(true);
        generateBtn.setText("Regenerate");
        generatedLabel.setText(generatedText(itinerary.generatedAt()));

        renderConsensusBudgetBanner(itinerary);

        int totalOptions = 0;
        int visibleOptions = 0;

        daysBox.getChildren().clear();
        List<Itinerary.Day> days = itinerary.days() == null ? List.of() : itinerary.days();
        for (int i = 0; i < days.size(); i++) {
            Itinerary.Day day = days.get(i);
            totalOptions += countDayOptions(day);
            DayRenderResult rendered = dayCard(i + 1, day);
            if (rendered.visibleCount() > 0) {
                daysBox.getChildren().add(rendered.card());
                visibleOptions += rendered.visibleCount();
            }
        }

        if (visibleOptions == 0) {
            VBox noMatchBox = new VBox(10);
            noMatchBox.setAlignment(Pos.CENTER);
            noMatchBox.getStyleClass().add("card");
            Label msg = new Label("No activity options match the current filter.");
            msg.getStyleClass().add("item-title");
            Button reset = new Button("Reset Filters");
            reset.getStyleClass().addAll("btn-primary", "btn-sm");
            reset.setOnAction(this::handleResetFilters);
            noMatchBox.getChildren().addAll(msg, reset);
            daysBox.getChildren().add(noMatchBox);
        }

        filterSummaryLabel.setText(winnersOnlyMode
                ? "Showing " + visibleOptions + " elected consensus " + (visibleOptions == 1 ? "activity" : "activities")
                : "Showing " + visibleOptions + " of " + totalOptions + " options");
    }

    private void renderConsensusBudgetBanner(Itinerary itinerary) {
        if (winningOptionIds.isEmpty()) {
            consensusBudgetCard.setVisible(false);
            consensusBudgetCard.setManaged(false);
            return;
        }

        BigDecimal totalWinnerCostPerPerson = BigDecimal.ZERO;
        int winnerCount = 0;
        List<Itinerary.Day> days = itinerary.days() == null ? List.of() : itinerary.days();
        for (Itinerary.Day d : days) {
            if (d.blocks() == null) continue;
            for (Itinerary.Block b : d.blocks()) {
                if (b.subgroups() == null) continue;
                for (Itinerary.Subgroup s : b.subgroups()) {
                    if (s.options() == null) continue;
                    for (Itinerary.Option o : s.options()) {
                        if (isWinningOption(o)) {
                            winnerCount++;
                            if (o.estimatedCostUsd() != null) {
                                totalWinnerCostPerPerson = totalWinnerCostPerPerson.add(o.estimatedCostUsd());
                            }
                        }
                    }
                }
            }
        }

        if (winnerCount == 0) {
            consensusBudgetCard.setVisible(false);
            consensusBudgetCard.setManaged(false);
            return;
        }

        consensusBudgetCard.setVisible(true);
        consensusBudgetCard.setManaged(true);

        int numTravelers = Math.max(1, participantById.size());
        BigDecimal totalTripBudget = trip != null && trip.totalBudget() != null ? trip.totalBudget() : BigDecimal.ZERO;
        BigDecimal perTravelerTripBudget = totalTripBudget.divide(BigDecimal.valueOf(numTravelers), 2, RoundingMode.HALF_UP);
        BigDecimal groupActivitiesTotal = totalWinnerCostPerPerson.multiply(BigDecimal.valueOf(numTravelers));

        consensusTitleLabel.setText("Consensus \"Perfect Days\" — " + winnerCount
                + (winnerCount == 1 ? " Winning Activity Elected" : " Winning Activities Elected"));
        consensusCostLabel.setText("Estimated winning activities cost: " + money(totalWinnerCostPerPerson)
                + " / person (" + money(groupActivitiesTotal) + " across " + numTravelers
                + (numTravelers == 1 ? " traveler" : " travelers")
                + " out of " + money(totalTripBudget) + " total trip budget)");

        boolean withinBudget = totalTripBudget.signum() == 0 || totalWinnerCostPerPerson.compareTo(perTravelerTripBudget) <= 0;
        consensusBudgetBadge.setText(withinBudget ? "Within Trip Budget" : "Above Per-Person Budget");
        UiTheme.setBadgeVariant(consensusBudgetBadge, withinBudget ? "badge-success" : "badge-danger");
    }

    private record DayRenderResult(VBox card, int visibleCount) {}

    private DayRenderResult dayCard(int dayNumber, Itinerary.Day day) {
        Label badge = new Label("DAY " + dayNumber);
        badge.getStyleClass().addAll("badge", "badge-dark");
        Label date = new Label(niceDate(day.date()));
        date.getStyleClass().add("section-title");
        HBox header = new HBox(12, badge, date);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, header);
        card.getStyleClass().add("card");
        int visibleInDay = 0;

        if (day.blocks() != null) {
            for (Itinerary.Block block : day.blocks()) {
                DayRenderResult blockRendered = blockSection(block);
                if (blockRendered.visibleCount() > 0) {
                    card.getChildren().add(blockRendered.card());
                    visibleInDay += blockRendered.visibleCount();
                }
            }
        }
        return new DayRenderResult(card, visibleInDay);
    }

    private DayRenderResult blockSection(Itinerary.Block block) {
        Label title = new Label(BLOCK_TITLES.getOrDefault(block.timeBlock(), block.timeBlock()));
        title.getStyleClass().add("card-title");
        VBox section = new VBox(10, title);
        int visibleInBlock = 0;

        String selectedTravelerLabel = travelerFilterCombo.getValue();
        Long filterUserId = (selectedTravelerLabel != null && !ALL_TRAVELERS.equals(selectedTravelerLabel))
                ? travelerIdByFilterLabel.get(selectedTravelerLabel)
                : null;

        List<Itinerary.Subgroup> subgroups = block.subgroups() == null ? List.of() : block.subgroups();
        boolean split = subgroups.size() > 1;
        for (int i = 0; i < subgroups.size(); i++) {
            Itinerary.Subgroup sub = subgroups.get(i);
            if (filterUserId != null && sub.subgroupParticipants() != null && !sub.subgroupParticipants().isEmpty()
                    && !sub.subgroupParticipants().contains(filterUserId)) {
                continue;
            }

            List<Itinerary.Option> filteredOptions = filterAndSortOptions(sub.options());
            if (filteredOptions.isEmpty()) {
                continue;
            }

            if (split) section.getChildren().add(subgroupLabel(i + 1, sub));

            HBox optionsRow = new HBox(14);
            for (int n = 0; n < filteredOptions.size(); n++) {
                VBox optionCard = optionCard(n + 1, filteredOptions.get(n));
                HBox.setHgrow(optionCard, Priority.ALWAYS);
                optionCard.setPrefWidth(0);
                optionCard.setMaxWidth(Double.MAX_VALUE);
                optionsRow.getChildren().add(optionCard);
                visibleInBlock++;
            }
            section.getChildren().add(optionsRow);
        }
        return new DayRenderResult(section, visibleInBlock);
    }

    private List<Itinerary.Option> filterAndSortOptions(List<Itinerary.Option> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) return List.of();

        String categoryFilter = categoryFilterCombo.getValue();
        String sortOrder = sortCombo.getValue();

        List<Itinerary.Option> filtered = new ArrayList<>();
        for (Itinerary.Option opt : rawOptions) {
            if (winnersOnlyMode && !isWinningOption(opt)) {
                continue;
            }
            if (categoryFilter != null && !ALL_CATEGORIES.equals(categoryFilter)) {
                if (opt.category() == null || !opt.category().equalsIgnoreCase(categoryFilter)) {
                    continue;
                }
            }
            filtered.add(opt);
        }

        if (SORT_CHEAPEST.equals(sortOrder)) {
            filtered.sort(Comparator.comparing(
                    o -> o.estimatedCostUsd() == null ? BigDecimal.ZERO : o.estimatedCostUsd()
            ));
        } else if (SORT_PRICIEST.equals(sortOrder)) {
            filtered.sort(Comparator.comparing(
                    (Itinerary.Option o) -> o.estimatedCostUsd() == null ? BigDecimal.ZERO : o.estimatedCostUsd()
            ).reversed());
        } else if (SORT_EARLIEST.equals(sortOrder)) {
            filtered.sort(Comparator.comparing(
                    o -> o.startTime() == null ? "99:99" : o.startTime()
            ));
        }
        return filtered;
    }

    private static int countDayOptions(Itinerary.Day day) {
        if (day == null || day.blocks() == null) return 0;
        int count = 0;
        for (Itinerary.Block b : day.blocks()) {
            if (b.subgroups() == null) continue;
            for (Itinerary.Subgroup s : b.subgroups()) {
                if (s.options() != null) count += s.options().size();
            }
        }
        return count;
    }

    private Label subgroupLabel(int number, Itinerary.Subgroup sub) {
        List<Long> people = sub.subgroupParticipants() == null ? List.of() : sub.subgroupParticipants();
        String who;
        if (!participantById.isEmpty() && !people.isEmpty()) {
            who = people.stream()
                    .map(id -> {
                        Participant p = participantById.get(id);
                        return p != null ? p.shortName() : "Traveler " + id;
                    })
                    .collect(Collectors.joining(", "));
        } else {
            who = (people.size() == 1 ? "Traveler " : "Travelers ")
                    + people.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        String reason = sub.splitReason() == null || sub.splitReason().isBlank() ? "" : " · " + sub.splitReason();
        Label label = new Label("Group " + number + ": " + who + reason);
        label.setWrapText(true);
        label.getStyleClass().addAll("badge", "badge-violet");
        return label;
    }

    private VBox optionCard(int number, Itinerary.Option option) {
        boolean isWinner = isWinningOption(option);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label pick = new Label(winnersOnlyMode ? "ELECTED ACTIVITY" : "OPTION " + number);
        pick.getStyleClass().add("subtle-text");
        pick.setStyle("-fx-font-weight: bold;");
        topRow.getChildren().add(pick);

        if (isWinner) {
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label winnerBadge = new Label("GROUP WINNER");
            winnerBadge.getStyleClass().addAll("badge", "badge-amber");
            topRow.getChildren().addAll(sp, winnerBadge);
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
        if (option.whyItFits() != null && !option.whyItFits().isBlank()) {
            Region line = new Region();
            line.getStyleClass().add("divider");
            Label why = wrapped(option.whyItFits(), "meta-text");
            why.setStyle("-fx-font-style: italic;");
            card.getChildren().addAll(line, why);
        }

        card.getStyleClass().add(isWinner ? "option-card-winner" : "option-card");
        return card;
    }

    // ---------- formatting helpers ----------

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String costText(BigDecimal cost) {
        if (cost == null) return "Cost unknown";
        if (cost.signum() == 0) return "Free";
        return money(cost) + " / person";
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
            return isoDate;
        }
    }

    private static String generatedText(String generatedAt) {
        if (generatedAt == null) return "";
        try {
            return "Generated " + OffsetDateTime.parse(generatedAt).atZoneSameInstant(ZoneId.systemDefault()).format(GENERATED_AT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---------- status + busy helpers ----------

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
