package com.wudjabudja.controllers;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripType;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.ItineraryService;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.PreferenceService;
import com.wudjabudja.services.TripService;
import com.wudjabudja.session.AppSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Shared Trip Screen (GRP-03).
 * - Displays all travelers on the trip (with role + whether their quiz is saved).
 * - Allows the trip creator to add travelers by email (POST /api/trips/{id}/participants).
 * - Allows picking which traveler to enter/edit quiz answers for (navigates to PreferenceView).
 * - Displays the group's generated activity options (including subgroup splits and Borda winners).
 */
public class TripsController implements Initializable {

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US);
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

    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; " +
            "-fx-padding: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 8, 0, 0, 2);";

    @FXML private Label joinCodeBadge;
    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private Button generateBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private Label statusLabel;

    @FXML private Label travelersSummaryLabel;
    @FXML private TextField travelerEmailField;
    @FXML private Button addTravelerBtn;
    @FXML private ProgressIndicator addTravelerSpinner;
    @FXML private Label travelerStatusLabel;
    @FXML private VBox participantsListBox;

    @FXML private Label generatedAtLabel;
    @FXML private VBox emptyOptionsBox;
    @FXML private VBox daysBox;

    private final TripService tripService = new TripService();
    private final ParticipantService participantService = new ParticipantService();
    private final ItineraryService itineraryService = new ItineraryService();
    private final PreferenceService preferenceService = new PreferenceService();

    private Trip trip;
    private List<Participant> currentParticipants = new ArrayList<>();
    private final Map<Long, Participant> participantById = new HashMap<>();
    private Timeline clock;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create or open one first.", true);
            generateBtn.setDisable(true);
            addTravelerBtn.setDisable(true);
            return;
        }

        String flash = AppSession.takeFlashMessage();
        if (flash != null) {
            showStatus(flash, false);
        }

        showTripHeader(trip);
        loadSharedTripData();
    }

    private record SharedTripSnapshot(
            ApiResult<Trip> tripResult,
            ApiResult<List<Participant>> participantsResult,
            List<Participant> fallbackParticipants,
            ApiResult<Itinerary> itineraryResult
    ) {}

    private void loadSharedTripData() {
        if (trip == null) return;
        Long tripId = trip.id();
        setBusy(true, "Loading shared trip roster and group options...");

        Task<SharedTripSnapshot> task = new Task<>() {
            @Override
            protected SharedTripSnapshot call() {
                ApiResult<Trip> tRes = tripService.getTrip(tripId);
                ApiResult<List<Participant>> pRes = participantService.listParticipants(tripId);
                List<Participant> fallback = List.of();
                if (!pRes.success() || pRes.data() == null || pRes.data().isEmpty()) {
                    PreferenceService.LoadResult prefCheck = preferenceService.loadPreferences(tripId, AppSession.USER_ID);
                    fallback = List.of(new Participant(
                            AppSession.USER_ID,
                            AppSession.DEFAULT_EMAIL,
                            AppSession.DEFAULT_NAME,
                            "leader",
                            prefCheck.isSuccess()
                    ));
                }
                ApiResult<Itinerary> iRes = itineraryService.loadItinerary(tripId);
                return new SharedTripSnapshot(tRes, pRes, fallback, iRes);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            SharedTripSnapshot snap = task.getValue();

            if (snap.tripResult().success() && snap.tripResult().data() != null) {
                trip = snap.tripResult().data();
                AppSession.setCurrentTrip(trip);
                showTripHeader(trip);
            }

            if (snap.participantsResult().success() && snap.participantsResult().data() != null
                    && !snap.participantsResult().data().isEmpty()) {
                renderParticipants(snap.participantsResult().data());
            } else {
                renderParticipants(snap.fallbackParticipants());
                if (!snap.participantsResult().success() && !snap.participantsResult().isNotFound()) {
                    showTravelerStatus("✗ Couldn't load travelers: " + snap.participantsResult().message(), true);
                }
            }

            ApiResult<Itinerary> itRes = snap.itineraryResult();
            if (itRes != null && itRes.success() && itRes.data() != null) {
                renderItinerary(itRes.data());
            } else if (itRes != null && itRes.isNotFound()) {
                showEmptyOptionsState();
            } else if (itRes != null) {
                showStatus("✗ " + itRes.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("✗ Failed to load shared trip data: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-load");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        clearStatus();
        clearTravelerStatus();
        loadSharedTripData();
    }

    // ---------- Add Traveler by Email (GRP-03 -> GRP-02) ----------

    @FXML
    public void handleAddTraveler(ActionEvent event) {
        if (trip == null) return;
        clearTravelerStatus();

        String email = travelerEmailField.getText() == null ? "" : travelerEmailField.getText().trim();
        if (email.isEmpty()) {
            showTravelerStatus("✗ Please enter a traveler's email address.", true);
            return;
        }
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@") || !email.contains(".")) {
            showTravelerStatus("✗ Please enter a valid email address (for example: alex@example.com).", true);
            return;
        }

        setAddingTraveler(true);
        Long tripId = trip.id();

        Task<ApiResult<Participant>> task = new Task<>() {
            @Override
            protected ApiResult<Participant> call() {
                return participantService.addParticipant(tripId, email);
            }
        };

        task.setOnSucceeded(e -> {
            setAddingTraveler(false);
            ApiResult<Participant> result = task.getValue();
            if (result.success()) {
                travelerEmailField.clear();
                Participant added = result.data();
                if (added != null && added.userId() != null) {
                    AppSession.setActiveTraveler(added);
                }
                showTravelerStatus("✓ Added " + email + " to the trip! Click \"Take / Edit Quiz\" next to their name to enter their preferences.", false);
                loadSharedTripData();
            } else {
                showTravelerStatus("✗ " + result.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setAddingTraveler(false);
            showTravelerStatus("✗ Error adding traveler: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-add-traveler");
    }

    // ---------- Generate Group Options ----------

    @FXML
    public void handleGenerateOptions(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();

        clearStatus();
        Instant since = Instant.now();
        String waitMsg = "Gemini is generating group options for all travelers on this trip...";
        setBusy(true, waitMsg);
        generateBtn.setDisable(true);

        clock = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            long secs = java.time.Duration.between(since, Instant.now()).toSeconds();
            busyLabel.setText(waitMsg + String.format(" (%d:%02d)", secs / 60, secs % 60));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        Task<ApiResult<Itinerary>> task = new Task<>() {
            @Override
            protected ApiResult<Itinerary> call() {
                return itineraryService.generateItinerary(tripId);
            }
        };

        task.setOnSucceeded(e -> {
            if (clock != null) clock.stop();
            setBusy(false, null);
            generateBtn.setDisable(false);
            ApiResult<Itinerary> res = task.getValue();
            if (res.success() && res.data() != null) {
                renderItinerary(res.data());
                showStatus("✓ Group options generated! Review the options below or head to Group Voting.", false);
            } else {
                showStatus("✗ " + res.message(), true);
            }
        });

        task.setOnFailed(e -> {
            if (clock != null) clock.stop();
            setBusy(false, null);
            generateBtn.setDisable(false);
            showStatus("✗ Something went wrong while generating options: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-generate");
    }

    // ---------- Rendering Travelers ----------

    private void renderParticipants(List<Participant> participants) {
        this.currentParticipants = participants == null ? List.of() : List.copyOf(participants);
        this.participantById.clear();
        for (Participant p : this.currentParticipants) {
            if (p.userId() != null) {
                participantById.put(p.userId(), p);
            }
        }

        // Keep AppSession's active traveler synced with the latest participant info
        Participant active = participantById.get(AppSession.getActiveUserId());
        if (active != null) {
            AppSession.setActiveTraveler(active);
        } else if (!this.currentParticipants.isEmpty()) {
            AppSession.setActiveTraveler(this.currentParticipants.get(0));
        }

        long savedCount = this.currentParticipants.stream().filter(Participant::quizSaved).count();
        int total = this.currentParticipants.size();
        travelersSummaryLabel.setText(
                savedCount + " of " + total + (total == 1 ? " traveler has" : " travelers have")
                + " saved their preferences quiz · Active traveler: " + AppSession.getActiveTraveler().shortName()
        );

        participantsListBox.getChildren().clear();
        for (Participant p : this.currentParticipants) {
            participantsListBox.getChildren().add(participantRow(p));
        }
    }

    private HBox participantRow(Participant p) {
        boolean isActive = p.userId() != null && p.userId().equals(AppSession.getActiveUserId());

        String nameText = p.fullName() != null && !p.fullName().isBlank()
                ? p.fullName().trim()
                : (p.email() != null && !p.email().isBlank() ? p.email().trim() : "Traveler #" + p.userId());
        Label nameLabel = new Label(nameText);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 14px;");

        String emailSub = (p.email() != null && !p.email().isBlank() ? p.email().trim() : "")
                + (p.userId() != null ? "  (Traveler ID: " + p.userId() + ")" : "");
        Label emailLabel = new Label(emailSub);
        emailLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        VBox identityBox = new VBox(2, nameLabel, emailLabel);

        // Role badge
        Label roleBadge = new Label(p.isLeader() ? "👑 LEADER" : "🧳 MEMBER");
        roleBadge.setStyle(p.isLeader()
                ? "-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 3 8; -fx-background-radius: 10;"
                : "-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 3 8; -fx-background-radius: 10;");

        // Quiz status badge
        Label quizBadge = new Label(p.quizSaved() ? "✓ Quiz Saved" : "⏳ Quiz Pending");
        quizBadge.setStyle(p.quizSaved()
                ? "-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 10;"
                : "-fx-background-color: #ffedd5; -fx-text-fill: #9a3412; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 10;");

        // Active traveler indicator
        Label activeBadge = new Label(isActive ? "● Active" : "");
        activeBadge.setVisible(isActive);
        activeBadge.setManaged(isActive);
        activeBadge.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 11px;");

        // Action: Take / Edit Quiz for this traveler
        Button quizBtn = new Button(p.quizSaved() ? "⚙️ Edit Quiz" : "📝 Take Quiz");
        quizBtn.setStyle(p.quizSaved()
                ? "-fx-background-color: white; -fx-border-color: #2563eb; -fx-border-radius: 8; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;"
                : "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 14; -fx-background-radius: 8; -fx-cursor: hand;");
        quizBtn.setOnAction(e -> {
            AppSession.setActiveTraveler(p);
            PreferenceService.setCurrentPreferences(null);
            NavigationManager.getInstance().navigateTo("preferences");
        });

        // Action: Vote as this traveler
        Button voteBtn = new Button("🗳️ Vote");
        voteBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-padding: 6 12; -fx-cursor: hand;");
        voteBtn.setOnAction(e -> {
            AppSession.setActiveTraveler(p);
            NavigationManager.getInstance().navigateTo("voting");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, identityBox, roleBadge, quizBadge, activeBadge, spacer, quizBtn, voteBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(isActive
                ? "-fx-background-color: #eff6ff; -fx-border-color: #93c5fd; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12 16;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12 16;");
        return row;
    }

    // ---------- Rendering Group Options ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText(t.title());
        joinCodeBadge.setText(t.joinCode() != null && !t.joinCode().isBlank() ? "Code: " + t.joinCode() : "Trip #" + t.id());
        tripDetailsLabel.setText("📍 " + t.destination()
                + "   📅 " + t.dateRangeText() + " (" + t.numDays() + (t.numDays() == 1 ? " day)" : " days)")
                + "   🧳 " + TripType.labelFor(t.tripType())
                + "   💰 " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyOptionsState() {
        daysBox.getChildren().clear();
        emptyOptionsBox.setVisible(true);
        emptyOptionsBox.setManaged(true);
        generateBtn.setText("✨ Generate Group Options");
        generatedAtLabel.setText("");
    }

    private void renderItinerary(Itinerary itinerary) {
        emptyOptionsBox.setVisible(false);
        emptyOptionsBox.setManaged(false);
        generateBtn.setText("🔄 Regenerate Group Options");
        generatedAtLabel.setText(generatedText(itinerary.generatedAt()));

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
            section.getChildren().add(subgroupLabel(i + 1, sub, split));

            HBox options = new HBox(12);
            List<Itinerary.Option> list = sub.options() == null ? List.of() : sub.options();
            for (int n = 0; n < list.size(); n++) {
                VBox optionCard = optionCard(n + 1, list.get(n));
                HBox.setHgrow(optionCard, Priority.ALWAYS);
                optionCard.setPrefWidth(0);
                optionCard.setMaxWidth(Double.MAX_VALUE);
                options.getChildren().add(optionCard);
            }
            section.getChildren().add(options);
        }
        return section;
    }

    private Label subgroupLabel(int number, Itinerary.Subgroup sub, boolean split) {
        List<Long> people = sub.subgroupParticipants() == null ? List.of() : sub.subgroupParticipants();
        String names = people.stream()
                .map(id -> {
                    Participant p = participantById.get(id);
                    return p != null ? p.shortName() : "Traveler " + id;
                })
                .collect(Collectors.joining(", "));
        if (names.isEmpty()) {
            names = "All travelers";
        }
        String prefix = split ? "👥 Subgroup " + number + " (" + names + ")" : "👥 Travelers: " + names;
        String reason = (sub.splitReason() == null || sub.splitReason().isBlank()) ? "" : " · " + sub.splitReason();
        Label label = new Label(prefix + reason);
        label.setWrapText(true);
        label.setStyle(split
                ? "-fx-text-fill: #7c3aed; -fx-font-weight: bold; -fx-font-size: 12px;"
                : "-fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 12px;");
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
        String name = category == null || category.isBlank() ? "other" : category;
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

    // ---------- Navigation Actions ----------

    @FXML
    public void goToTripSetup(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("tripsetup");
    }

    @FXML
    public void goToVoting(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("voting");
    }

    @FXML
    public void goToItinerary(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("itinerary");
    }

    // ---------- Formatting & Status Helpers ----------

    private static Label wrapped(String text, String style) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle(style);
        return label;
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

    private void setBusy(boolean busy, String message) {
        busyBox.setVisible(busy);
        busyBox.setManaged(busy);
        busyLabel.setText(message == null ? "" : message);
    }

    private void setAddingTraveler(boolean busy) {
        addTravelerBtn.setDisable(busy);
        travelerEmailField.setDisable(busy);
        addTravelerSpinner.setVisible(busy);
        addTravelerSpinner.setManaged(busy);
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

    private void showTravelerStatus(String message, boolean isError) {
        travelerStatusLabel.setText(message);
        travelerStatusLabel.setVisible(true);
        travelerStatusLabel.setManaged(true);
        travelerStatusLabel.setStyle(isError
                ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;"
                : "-fx-text-fill: #059669; -fx-font-weight: bold;");
    }

    private void clearTravelerStatus() {
        travelerStatusLabel.setText("");
        travelerStatusLabel.setVisible(false);
        travelerStatusLabel.setManaged(false);
    }

    private static void startInBackground(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
