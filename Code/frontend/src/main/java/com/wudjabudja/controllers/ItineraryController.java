package com.wudjabudja.controllers;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripType;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.ItineraryService;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.TripService;
import com.wudjabudja.session.AppSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Itinerary screen (MVP-09). On open it loads the saved itinerary for the current trip.
 * Generate / Regenerate asks the backend to call Gemini, which can take a minute or two,
 * so that runs in a background Task with a spinner while the buttons are disabled.
 */
public class ItineraryController implements Initializable {

    // Generation keeps going if you click to another screen. These remember it, so coming
    // back shows the spinner (and the result) instead of letting you start a second one.
    private static Task<ApiResult<Itinerary>> runningTask;
    private static Long runningTripId;
    private static Instant runningSince;

    private static final String GEMINI_WAIT_TEXT = "Gemini is planning your trip, this can take a minute or two...";

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "morning", "🌅  Morning", "afternoon", "☀️  Afternoon", "evening", "🌙  Evening");

    // Chip colors per category: background, text
    private static final Map<String, String[]> CATEGORY_COLORS = Map.of(
            "food", new String[]{"#fef3c7", "#92400e"},
            "outdoors", new String[]{"#dcfce7", "#166534"},
            "culture", new String[]{"#ede9fe", "#5b21b6"},
            "nightlife", new String[]{"#e0e7ff", "#3730a3"},
            "sports", new String[]{"#fee2e2", "#991b1b"},
            "shopping", new String[]{"#fce7f3", "#9d174d"},
            "relaxation", new String[]{"#e0f2fe", "#075985"},
            "tour", new String[]{"#f1f5f9", "#334155"});

    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; " +
            "-fx-padding: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 8, 0, 0, 2);";

    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private Label generatedLabel;
    @FXML private Button generateBtn;
    @FXML private Button editPrefsBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private Label statusLabel;
    @FXML private VBox emptyStateBox;
    @FXML private VBox daysBox;

    private final TripService tripService = new TripService();
    private final ItineraryService itineraryService = new ItineraryService();
    private final ParticipantService participantService = new ParticipantService();

    private Trip trip;
    private final Map<Long, Participant> participantById = new HashMap<>();
    private Timeline clock;   // ticks the "(0:42)" timer while Gemini works

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            // MainController normally sends you to Plan a Trip first, so this is just a safety net
            showStatus("No trip selected. Use \"Plan a Trip\" to create one first.", true);
            generateBtn.setDisable(true);
            return;
        }
        showTripHeader(trip);

        if (runningTask != null && trip.id().equals(runningTripId) && !runningTask.isDone()) {
            watchGeneration(runningTask);   // a generation for this trip is still running
        } else {
            loadSaved();
        }
    }

    // ---------- loading what's already saved ----------

    // What we learned when the screen opened
    private record LoadOutcome(ApiResult<Trip> trip, ApiResult<List<Participant>> participants, ApiResult<Itinerary> itinerary) {}

    private void loadSaved() {
        setBusy(true, "Loading your saved itinerary...");
        Long tripId = trip.id();
        Task<LoadOutcome> task = new Task<>() {
            @Override
            protected LoadOutcome call() {
                ApiResult<Trip> tripResult = tripService.getTrip(tripId);
                if (!tripResult.success()) return new LoadOutcome(tripResult, null, null);
                ApiResult<List<Participant>> partResult = participantService.listParticipants(tripId);
                return new LoadOutcome(tripResult, partResult, itineraryService.loadItinerary(tripId));
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false, null);
            showLoadOutcome(task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("✗ Couldn't load the itinerary: " + task.getException().getMessage(), true);
        });
        startInBackground(task, "itinerary-load");
    }

    private void showLoadOutcome(LoadOutcome outcome) {
        ApiResult<Trip> tripResult = outcome.trip();
        if (!tripResult.success()) {
            if (tripResult.isNotFound()) {
                // e.g. the database was rebuilt while the app was open
                showStatus("✗ Trip #" + trip.id() + " isn't in the database anymore. Use \"Plan a Trip\" to make a new one.", true);
                generateBtn.setDisable(true);
            } else {
                showStatus("✗ " + tripResult.message(), true);
            }
            return;
        }

        // Use the fresh copy from the server for the header
        trip = tripResult.data();
        AppSession.setCurrentTrip(trip);
        showTripHeader(trip);

        participantById.clear();
        if (outcome.participants() != null && outcome.participants().success() && outcome.participants().data() != null) {
            for (Participant p : outcome.participants().data()) {
                if (p.userId() != null) participantById.put(p.userId(), p);
            }
        }

        ApiResult<Itinerary> itineraryResult = outcome.itinerary();
        if (itineraryResult.success()) {
            showItinerary(itineraryResult.data());
        } else if (itineraryResult.isNotFound()) {
            showEmptyState();
        } else {
            showStatus("✗ " + itineraryResult.message(), true);
        }
    }

    // ---------- generating ----------

    @FXML
    public void handleGenerate(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();

        Task<ApiResult<Itinerary>> task = new Task<>() {
            @Override
            protected ApiResult<Itinerary> call() {
                return itineraryService.generateItinerary(tripId);   // up to ~3 minutes, never on the UI thread
            }
        };
        // Registered first, so it runs before any screen's handler: forget the task once it's done
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> forgetRunningTask(task));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> forgetRunningTask(task));

        runningTask = task;
        runningTripId = tripId;
        runningSince = Instant.now();

        clearStatus();
        watchGeneration(task);
        startInBackground(task, "itinerary-generate");
    }

    // Show the spinner for a running generation and update this screen when it finishes
    private void watchGeneration(Task<ApiResult<Itinerary>> task) {
        setGenerating(true);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            setGenerating(false);
            ApiResult<Itinerary> result = task.getValue();
            if (result.success()) {
                showItinerary(result.data());
                showStatus("✓ Your itinerary is ready. Pick a favorite in each time block!", false);
            } else {
                // Keep showing the previous itinerary (if any) and say exactly what went wrong
                showStatus("✗ " + result.message(), true);
            }
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            setGenerating(false);
            showStatus("✗ Something went wrong while generating: " + task.getException().getMessage(), true);
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

    @FXML
    public void goToPreferences(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("preferences");
    }

    @FXML
    public void goToSharedTrip(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("trips");
    }

    @FXML
    public void goToVoting(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("voting");
    }

    // ---------- drawing the itinerary ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText(t.title());
        tripDetailsLabel.setText("📍 " + t.destination()
                + "   📅 " + t.dateRangeText() + " (" + t.numDays() + (t.numDays() == 1 ? " day)" : " days)")
                + "   🧳 " + TripType.labelFor(t.tripType())
                + "   💰 " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyState() {
        daysBox.getChildren().clear();
        emptyStateBox.setVisible(true);
        emptyStateBox.setManaged(true);
        generateBtn.setText("✨ Generate my itinerary");
        generatedLabel.setText("");
    }

    private void showItinerary(Itinerary itinerary) {
        emptyStateBox.setVisible(false);
        emptyStateBox.setManaged(false);
        generateBtn.setText("🔄 Regenerate");
        generatedLabel.setText(generatedText(itinerary.generatedAt()));

        daysBox.getChildren().clear();
        List<Itinerary.Day> days = itinerary.days() == null ? List.of() : itinerary.days();
        for (int i = 0; i < days.size(); i++) {
            daysBox.getChildren().add(dayCard(i + 1, days.get(i)));
        }
    }

    private VBox dayCard(int dayNumber, Itinerary.Day day) {
        Label badge = new Label("DAY " + dayNumber);
        badge.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 12;");
        Label date = new Label(niceDate(day.date()));
        date.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #0f172a;");
        HBox header = new HBox(12, badge, date);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, header);
        card.setStyle(CARD_STYLE);
        if (day.blocks() != null) {
            for (Itinerary.Block block : day.blocks()) {
                card.getChildren().add(blockSection(block));
            }
        }
        return card;
    }

    private VBox blockSection(Itinerary.Block block) {
        Label title = new Label(BLOCK_TITLES.getOrDefault(block.timeBlock(), block.timeBlock()));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        VBox section = new VBox(10, title);

        List<Itinerary.Subgroup> subgroups = block.subgroups() == null ? List.of() : block.subgroups();
        boolean split = subgroups.size() > 1;
        for (int i = 0; i < subgroups.size(); i++) {
            Itinerary.Subgroup sub = subgroups.get(i);
            if (split) section.getChildren().add(subgroupLabel(i + 1, sub));

            HBox options = new HBox(12);
            List<Itinerary.Option> list = sub.options() == null ? List.of() : sub.options();
            for (int n = 0; n < list.size(); n++) {
                VBox optionCard = optionCard(n + 1, list.get(n));
                // Equal widths: each card starts at 0 and grows into an equal share of the row
                HBox.setHgrow(optionCard, Priority.ALWAYS);
                optionCard.setPrefWidth(0);
                optionCard.setMaxWidth(Double.MAX_VALUE);
                options.getChildren().add(optionCard);
            }
            section.getChildren().add(options);
        }
        return section;
    }

    // "Group 1: Traveler 1 · they want live sports while..." when Gemini splits the group
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
        Label label = new Label("👥 Group " + number + ": " + who + reason);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #7c3aed; -fx-font-weight: bold; -fx-font-size: 12px;");
        return label;
    }

    private VBox optionCard(int number, Itinerary.Option option) {
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label pick = new Label("OPTION " + number);
        pick.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 10px;");
        topRow.getChildren().add(pick);

        if (option.isFinal()) {
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label winnerBadge = new Label("🏆 GROUP WINNER");
            winnerBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 8;");
            topRow.getChildren().addAll(sp, winnerBadge);
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
        if (option.whyItFits() != null && !option.whyItFits().isBlank()) {
            Region line = new Region();
            line.setPrefHeight(1);
            line.setStyle("-fx-background-color: #f1f5f9;");
            card.getChildren().addAll(line,
                    wrapped("💡 " + option.whyItFits(), "-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-style: italic;"));
        }

        card.setStyle(option.isFinal()
                ? "-fx-background-color: #fffbeb; -fx-border-color: #f59e0b; -fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");
        return card;
    }

    private Label categoryChip(String category) {
        String name = category == null ? "other" : category;
        String[] colors = CATEGORY_COLORS.getOrDefault(name, new String[]{"#f1f5f9", "#334155"});
        Label chip = new Label(name.substring(0, 1).toUpperCase() + name.substring(1));
        chip.setStyle("-fx-background-color: " + colors[0] + "; -fx-text-fill: " + colors[1]
                + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 10;");
        return chip;
    }

    private Label costChip(BigDecimal cost) {
        String text;
        if (cost == null) text = "Cost unknown";
        else if (cost.signum() == 0) text = "Free";
        else text = money(cost) + " / person";
        Label chip = new Label(text);
        chip.setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #047857; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 10;");
        return chip;
    }

    // ---------- formatting helpers ----------

    private static Label wrapped(String text, String style) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle(style);
        return label;
    }

    // "09:00", "11:30" -> "9:00 AM to 11:30 AM"
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

    // $25 or $12.50 (no ".00" on whole dollars)
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

    // The backend sends UTC; show it in this Mac's time zone
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
        thread.setDaemon(true);   // don't keep the app alive after the window closes
        thread.start();
    }
}
