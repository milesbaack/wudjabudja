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
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
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
import javafx.scene.layout.StackPane;
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
 * Displays all travelers on the trip, allows adding/removing travelers,
 * switches active traveler for quiz/voting, and displays generated group activity options.
 */
public class TripsController implements Initializable {

    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "morning", "Morning",
            "afternoon", "Afternoon",
            "evening", "Evening"
    );

    @FXML private Label joinCodeBadge;
    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private Button generateBtn;
    @FXML private Button refreshBtn;
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
        if (generateBtn != null) {
            generateBtn.setGraphic(Icons.create(Icons.SPARKLES, 15, "#FFFFFF"));
        }
        if (addTravelerBtn != null) {
            addTravelerBtn.setGraphic(Icons.create(Icons.PLUS, 15, "#FFFFFF"));
        }
        if (refreshBtn != null) {
            refreshBtn.setGraphic(Icons.create(Icons.REFRESH, 13, "#334155"));
        }

        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create or open one first.", true);
            generateBtn.setDisable(true);
            addTravelerBtn.setDisable(true);
            return;
        }

        String flash = AppSession.takeFlashMessage();
        if (flash != null) {
            showStatus(flash, AppSession.isLastFlashError());
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
                            AppSession.getLoggedInEmail(),
                            AppSession.getLoggedInName(),
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
                NavigationManager.getInstance().refreshSidebar();
                showTripHeader(trip);
            }

            if (snap.participantsResult().success() && snap.participantsResult().data() != null
                    && !snap.participantsResult().data().isEmpty()) {
                renderParticipants(snap.participantsResult().data());
            } else {
                renderParticipants(snap.fallbackParticipants());
                if (!snap.participantsResult().success() && !snap.participantsResult().isNotFound()) {
                    showTravelerStatus("Couldn't load travelers: " + snap.participantsResult().message(), true);
                }
            }

            ApiResult<Itinerary> itRes = snap.itineraryResult();
            if (itRes != null && itRes.success() && itRes.data() != null) {
                renderItinerary(itRes.data());
            } else if (itRes != null && itRes.isNotFound()) {
                showEmptyOptionsState();
            } else if (itRes != null) {
                showStatus(itRes.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("Failed to load shared trip data: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-load");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        clearStatus();
        clearTravelerStatus();
        loadSharedTripData();
    }

    // ---------- Add & Remove Travelers ----------

    @FXML
    public void handleAddTraveler(ActionEvent event) {
        if (trip == null) return;
        clearTravelerStatus();

        String email = travelerEmailField.getText() == null ? "" : travelerEmailField.getText().trim();
        if (email.isEmpty()) {
            showTravelerStatus("Please enter a traveler's email address.", true);
            return;
        }
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@") || !email.contains(".")) {
            showTravelerStatus("Please enter a valid email address (for example: alex@example.com).", true);
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
                showTravelerStatus("Added " + email + " to the trip. Click \"Take Quiz\" next to their name to enter their preferences.", false);
                loadSharedTripData();
            } else {
                showTravelerStatus(result.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setAddingTraveler(false);
            showTravelerStatus("Error adding traveler: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-add-traveler");
    }

    private void handleRemoveTraveler(Participant participant, Button removeBtn) {
        if (trip == null || participant == null || participant.userId() == null) return;
        clearTravelerStatus();

        Long tripId = trip.id();
        Long userId = participant.userId();
        String name = participant.shortName();
        removeBtn.setDisable(true);

        Task<ApiResult<Void>> task = new Task<>() {
            @Override
            protected ApiResult<Void> call() {
                return participantService.removeParticipant(tripId, userId);
            }
        };

        task.setOnSucceeded(e -> {
            ApiResult<Void> result = task.getValue();
            if (result.success()) {
                VotingController.clearVoterBallot(tripId, userId);
                if (userId.equals(AppSession.getActiveUserId())) {
                    Participant leader = currentParticipants.stream()
                            .filter(Participant::isLeader)
                            .findFirst()
                            .orElse(AppSession.defaultTraveler());
                    AppSession.setActiveTraveler(leader);
                    PreferenceService.setCurrentPreferences(null);
                }
                showTravelerStatus("Removed " + name + " from the trip.", false);
                loadSharedTripData();
            } else {
                removeBtn.setDisable(false);
                showTravelerStatus(result.message(), true);
            }
        });

        task.setOnFailed(e -> {
            removeBtn.setDisable(false);
            showTravelerStatus("Error removing traveler: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "shared-trip-remove-traveler");
    }

    // ---------- Generate Group Options ----------

    @FXML
    public void handleGenerateOptions(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();

        clearStatus();
        Instant since = Instant.now();
        String waitMsg = "Generating group options for all travelers on this trip...";
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
                showStatus("Group options generated. Review the options below or head to Group Voting.", false);
            } else {
                showStatus(res.message(), true);
            }
        });

        task.setOnFailed(e -> {
            if (clock != null) clock.stop();
            setBusy(false, null);
            generateBtn.setDisable(false);
            showStatus("Something went wrong while generating options: " + task.getException().getMessage(), true);
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
                + " saved their preferences quiz  ·  Active traveler: " + AppSession.getActiveTraveler().shortName()
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

        StackPane avatar = UiTheme.travelerAvatar(nameText);

        Label nameLabel = new Label(nameText);
        nameLabel.getStyleClass().add("item-title");

        String emailSub = (p.email() != null && !p.email().isBlank() ? p.email().trim() : "")
                + (p.userId() != null ? "  ·  ID #" + p.userId() : "");
        Label emailLabel = new Label(emailSub);
        emailLabel.getStyleClass().add("meta-text");

        VBox identityBox = new VBox(2, nameLabel, emailLabel);

        Label roleBadge = new Label(p.isLeader() ? "LEADER" : "MEMBER");
        roleBadge.getStyleClass().addAll("badge", p.isLeader() ? "badge-amber" : "badge-neutral");

        Label quizBadge = new Label(p.quizSaved() ? "Quiz Saved" : "Quiz Pending");
        quizBadge.getStyleClass().addAll("badge", p.quizSaved() ? "badge-success" : "badge-warning");

        HBox badges = new HBox(8, roleBadge, quizBadge);
        badges.setAlignment(Pos.CENTER_LEFT);
        if (isActive) {
            Label activeBadge = new Label("ACTIVE");
            activeBadge.getStyleClass().addAll("badge", "badge-teal");
            badges.getChildren().add(activeBadge);
        }

        Button quizBtn = new Button(p.quizSaved() ? "Edit Quiz" : "Take Quiz");
        quizBtn.getStyleClass().addAll(p.quizSaved() ? "btn-soft" : "btn-primary", "btn-sm");
        quizBtn.setOnAction(e -> {
            AppSession.setActiveTraveler(p);
            PreferenceService.setCurrentPreferences(null);
            NavigationManager.getInstance().navigateTo("preferences");
        });

        Button voteBtn = new Button("Vote");
        voteBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        voteBtn.setOnAction(e -> {
            AppSession.setActiveTraveler(p);
            NavigationManager.getInstance().navigateTo("voting");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, avatar, identityBox, badges, spacer, quizBtn, voteBtn);
        if (!p.isLeader() && p.userId() != null) {
            Button removeBtn = new Button("Remove");
            removeBtn.getStyleClass().addAll("btn-danger-outline", "btn-sm");
            removeBtn.setOnAction(e -> handleRemoveTraveler(p, removeBtn));
            row.getChildren().add(removeBtn);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(isActive ? "list-row-active" : "list-row");
        return row;
    }

    // ---------- Rendering Group Options ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText(t.title());
        joinCodeBadge.setText(t.joinCode() != null && !t.joinCode().isBlank() ? "Code: " + t.joinCode() : "Trip #" + t.id());
        tripDetailsLabel.setText(t.destination()
                + "  ·  " + t.dateRangeText() + " (" + t.numDays() + (t.numDays() == 1 ? " day)" : " days)")
                + "  ·  " + TripType.labelFor(t.tripType())
                + "  ·  " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyOptionsState() {
        daysBox.getChildren().clear();
        emptyOptionsBox.setVisible(true);
        emptyOptionsBox.setManaged(true);
        generateBtn.setText("Generate Group Options");
        generatedAtLabel.setText("");
    }

    private void renderItinerary(Itinerary itinerary) {
        emptyOptionsBox.setVisible(false);
        emptyOptionsBox.setManaged(false);
        generateBtn.setText("Regenerate Group Options");
        generatedAtLabel.setText(generatedText(itinerary.generatedAt()));

        daysBox.getChildren().clear();
        List<Itinerary.Day> days = itinerary.days() == null ? List.of() : itinerary.days();
        for (int i = 0; i < days.size(); i++) {
            daysBox.getChildren().add(dayCard(i + 1, days.get(i)));
        }
    }

    private VBox dayCard(int dayNumber, Itinerary.Day day) {
        Label badge = new Label("DAY " + dayNumber);
        badge.getStyleClass().addAll("badge", "badge-dark");
        Label date = new Label(niceDate(day.date()));
        date.getStyleClass().add("section-title");
        HBox header = new HBox(12, badge, date);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, header);
        card.getStyleClass().add("card");
        if (day.blocks() != null) {
            for (Itinerary.Block block : day.blocks()) {
                card.getChildren().add(blockSection(block));
            }
        }
        return card;
    }

    private VBox blockSection(Itinerary.Block block) {
        Label title = new Label(BLOCK_TITLES.getOrDefault(block.timeBlock(), block.timeBlock()));
        title.getStyleClass().add("card-title");
        VBox section = new VBox(10, title);

        List<Itinerary.Subgroup> subgroups = block.subgroups() == null ? List.of() : block.subgroups();
        boolean split = subgroups.size() > 1;
        for (int i = 0; i < subgroups.size(); i++) {
            Itinerary.Subgroup sub = subgroups.get(i);
            section.getChildren().add(subgroupLabel(i + 1, sub, split));

            HBox options = new HBox(14);
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
        String prefix = split ? "Subgroup " + number + " (" + names + ")" : "Travelers: " + names;
        String reason = (sub.splitReason() == null || sub.splitReason().isBlank()) ? "" : " · " + sub.splitReason();
        Label label = new Label(prefix + reason);
        label.setWrapText(true);
        if (split) {
            label.getStyleClass().addAll("badge", "badge-violet");
        } else {
            label.getStyleClass().add("meta-text");
        }
        return label;
    }

    private VBox optionCard(int number, Itinerary.Option option) {
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label pick = new Label("OPTION " + number);
        pick.getStyleClass().add("subtle-text");
        pick.setStyle("-fx-font-weight: bold;");
        topRow.getChildren().add(pick);

        if (option.isFinal()) {
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label winnerBadge = new Label("GROUP WINNER");
            winnerBadge.getStyleClass().addAll("badge", "badge-amber");
            topRow.getChildren().addAll(sp, winnerBadge);
        }

        Label title = wrapped(option.title(), "item-title");

        HBox timeRow = new HBox(6, Icons.create(Icons.CLOCK, 13, "#64748B"), wrapped(timeRange(option.startTime(), option.endTime()), "meta-text"));
        timeRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, topRow, title, timeRow);
        if (option.neighborhood() != null && !option.neighborhood().isBlank()) {
            HBox locRow = new HBox(6, Icons.create(Icons.MAP_PIN, 13, "#64748B"), wrapped(option.neighborhood(), "meta-text"));
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

        card.getStyleClass().add(option.isFinal() ? "option-card-winner" : "option-card");
        return card;
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
        UiTheme.showStatus(statusLabel, message, isError);
    }

    private void clearStatus() {
        UiTheme.clearStatus(statusLabel);
    }

    private void showTravelerStatus(String message, boolean isError) {
        UiTheme.showStatus(travelerStatusLabel, message, isError);
    }

    private void clearTravelerStatus() {
        UiTheme.clearStatus(travelerStatusLabel);
    }

    private static void startInBackground(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
