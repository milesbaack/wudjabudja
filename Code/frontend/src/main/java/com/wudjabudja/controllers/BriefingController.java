package com.wudjabudja.controllers;

import com.wudjabudja.models.Participant;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripBriefing;
import com.wudjabudja.models.TripType;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.BriefingService;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.TripService;
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
import javafx.scene.control.CheckBox;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the AI Trip Briefing Screen (BRIEF-03).
 * Loads or generates destination customs, language phrases, interactive packing checklist,
 * historical facts, crowd advisory, and personalized souvenirs.
 */
public class BriefingController implements Initializable {

    private static Task<ApiResult<TripBriefing>> runningTask;
    private static Long runningTripId;
    private static Instant runningSince;

    private static final String GEMINI_WAIT_TEXT =
            "Preparing your destination briefing (customs, phrases, packing list & souvenirs)...";

    @FXML private Label tripTitleLabel;
    @FXML private Label tripDetailsLabel;
    @FXML private Button generateBtn;
    @FXML private Button editPrefsBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private Label statusLabel;
    @FXML private VBox emptyStateBox;
    @FXML private VBox briefingContentBox;

    @FXML private Label crowdLevelBadge;
    @FXML private Label seasonSummaryLabel;
    @FXML private Label weatherNoteLabel;
    @FXML private VBox customsListBox;
    @FXML private Label primaryLanguageBadge;
    @FXML private VBox phrasesListBox;
    @FXML private Label packingProgressBadge;
    @FXML private VBox packingListBox;
    @FXML private VBox souvenirsListBox;
    @FXML private VBox factsListBox;

    private final TripService tripService = new TripService();
    private final ParticipantService participantService = new ParticipantService();
    private final BriefingService briefingService = new BriefingService();

    private Trip trip;
    private final Map<Long, Participant> participantById = new HashMap<>();
    private final List<CheckBox> packingCheckBoxes = new ArrayList<>();
    private Timeline clock;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (generateBtn != null) {
            generateBtn.setGraphic(Icons.create(Icons.SPARKLES, 15, "#FFFFFF"));
        }

        trip = AppSession.getCurrentTrip();
        if (trip == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create or open a trip first.", true);
            generateBtn.setDisable(true);
            return;
        }
        showTripHeader(trip);

        if (runningTask != null && trip.id().equals(runningTripId) && !runningTask.isDone()) {
            watchGeneration(runningTask);
        } else {
            loadSavedBriefing();
        }
    }

    private record BriefingLoadBundle(
            ApiResult<Trip> tripResult,
            ApiResult<List<Participant>> participantsResult,
            ApiResult<TripBriefing> briefingResult
    ) {}

    private void loadSavedBriefing() {
        setBusy(true, "Loading your saved trip briefing...");
        Long tripId = trip.id();

        Task<BriefingLoadBundle> task = new Task<>() {
            @Override
            protected BriefingLoadBundle call() {
                ApiResult<Trip> tRes = tripService.getTrip(tripId);
                ApiResult<List<Participant>> pRes = participantService.listParticipants(tripId);
                ApiResult<TripBriefing> bRes = briefingService.loadBriefing(tripId);
                return new BriefingLoadBundle(tRes, pRes, bRes);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            BriefingLoadBundle bundle = task.getValue();

            if (bundle.tripResult().success() && bundle.tripResult().data() != null) {
                trip = bundle.tripResult().data();
                AppSession.setCurrentTrip(trip);
                NavigationManager.getInstance().refreshSidebar();
                showTripHeader(trip);
            }

            participantById.clear();
            if (bundle.participantsResult() != null && bundle.participantsResult().success()
                    && bundle.participantsResult().data() != null) {
                for (Participant p : bundle.participantsResult().data()) {
                    if (p.userId() != null) {
                        participantById.put(p.userId(), p);
                    }
                }
            }

            ApiResult<TripBriefing> bRes = bundle.briefingResult();
            if (bRes != null && bRes.success() && bRes.data() != null) {
                renderBriefing(bRes.data());
            } else if (bRes != null && bRes.isNotFound()) {
                showEmptyState();
            } else if (bRes != null) {
                showEmptyState();
                showStatus(bRes.message(), true);
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            showStatus("Couldn't load trip briefing: " + task.getException().getMessage(), true);
        });

        startInBackground(task, "briefing-load");
    }

    @FXML
    public void handleGenerate(ActionEvent event) {
        if (trip == null) return;
        Long tripId = trip.id();

        Task<ApiResult<TripBriefing>> task = new Task<>() {
            @Override
            protected ApiResult<TripBriefing> call() {
                return briefingService.generateBriefing(tripId);
            }
        };
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> forgetRunningTask(task));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> forgetRunningTask(task));

        runningTask = task;
        runningTripId = tripId;
        runningSince = Instant.now();

        clearStatus();
        watchGeneration(task);
        startInBackground(task, "briefing-generate");
    }

    private void watchGeneration(Task<ApiResult<TripBriefing>> task) {
        setGenerating(true);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            setGenerating(false);
            ApiResult<TripBriefing> result = task.getValue();
            if (result.success() && result.data() != null) {
                renderBriefing(result.data());
                showStatus("Trip briefing ready for " + trip.destination() + ".", false);
            } else {
                showStatus(result.message(), true);
            }
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            setGenerating(false);
            showStatus("Failed to generate briefing: " + task.getException().getMessage(), true);
        });
    }

    private static void forgetRunningTask(Task<ApiResult<TripBriefing>> task) {
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

    // ---------- Rendering Live Briefing Data ----------

    private void showTripHeader(Trip t) {
        tripTitleLabel.setText("Trip Briefing — " + t.destination());
        tripDetailsLabel.setText(t.title()
                + "  ·  " + t.dateRangeText() + " (" + t.numDays() + (t.numDays() == 1 ? " day)" : " days)")
                + "  ·  " + TripType.labelFor(t.tripType())
                + "  ·  " + money(t.totalBudget()) + " total budget");
    }

    private void showEmptyState() {
        briefingContentBox.setVisible(false);
        briefingContentBox.setManaged(false);
        emptyStateBox.setVisible(true);
        emptyStateBox.setManaged(true);
        generateBtn.setText("Generate Trip Briefing");
    }

    private void renderBriefing(TripBriefing briefing) {
        emptyStateBox.setVisible(false);
        emptyStateBox.setManaged(false);
        briefingContentBox.setVisible(true);
        briefingContentBox.setManaged(true);
        generateBtn.setText("Regenerate Briefing");

        // 1. Season Advisory & Weather Note
        if (briefing.seasonAdvisory() != null) {
            String level = briefing.seasonAdvisory().crowdLevel() == null
                    ? "medium"
                    : briefing.seasonAdvisory().crowdLevel().trim().toLowerCase(Locale.ROOT);
            crowdLevelBadge.setText(level.toUpperCase(Locale.ROOT) + " CROWDS");
            if ("low".equals(level)) {
                UiTheme.setBadgeVariant(crowdLevelBadge, "badge-success");
            } else if ("high".equals(level)) {
                UiTheme.setBadgeVariant(crowdLevelBadge, "badge-danger");
            } else {
                UiTheme.setBadgeVariant(crowdLevelBadge, "badge-amber");
            }
            seasonSummaryLabel.setText(orEmpty(briefing.seasonAdvisory().summary()));
        } else {
            crowdLevelBadge.setText("ADVISORY");
            UiTheme.setBadgeVariant(crowdLevelBadge, "badge-sky");
            seasonSummaryLabel.setText("");
        }
        weatherNoteLabel.setText(orEmpty(briefing.weatherNote()));

        // 2. Cultural Customs
        customsListBox.getChildren().clear();
        for (TripBriefing.CustomTip custom : orEmptyList(briefing.customs())) {
            Label tipLabel = wrapped(orEmpty(custom.tip()), "item-title");
            Label whyLabel = wrapped(orEmpty(custom.why()), "meta-text");
            VBox box = new VBox(4, tipLabel, whyLabel);
            box.getStyleClass().add("list-row");
            customsListBox.getChildren().add(box);
        }

        // 3. Language & Phrases
        phrasesListBox.getChildren().clear();
        if (briefing.language() != null) {
            primaryLanguageBadge.setText(orEmpty(briefing.language().primaryLanguage()));
            for (TripBriefing.Phrase phrase : orEmptyList(briefing.language().phrases())) {
                Label exprLabel = wrapped("“" + orEmpty(phrase.phrase()) + "”", "item-title");
                exprLabel.setStyle("-fx-text-fill: #0F766E;");
                Label meaningLabel = wrapped(orEmpty(phrase.meaning()), "meta-text");
                VBox box = new VBox(4, exprLabel, meaningLabel);
                box.getStyleClass().add("list-row");
                phrasesListBox.getChildren().add(box);
            }
        } else {
            primaryLanguageBadge.setText("Local Language");
        }

        // 4. Interactive Packing Checklist with live progress counter
        packingListBox.getChildren().clear();
        packingCheckBoxes.clear();
        for (TripBriefing.PackingItem item : orEmptyList(briefing.packingList())) {
            CheckBox check = new CheckBox(orEmpty(item.item()));
            check.getStyleClass().add("item-title");
            Label reasonLabel = wrapped("— " + orEmpty(item.reason()), "meta-text");
            HBox row = new HBox(10, check, reasonLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");

            check.selectedProperty().addListener((obs, o, checked) -> {
                row.getStyleClass().removeAll("list-row", "list-row-checked");
                row.getStyleClass().add(checked ? "list-row-checked" : "list-row");
                updatePackingProgress();
            });

            row.setOnMouseClicked(e -> {
                if (e.getTarget() != check) {
                    check.setSelected(!check.isSelected());
                }
            });

            packingCheckBoxes.add(check);
            packingListBox.getChildren().add(row);
        }
        updatePackingProgress();

        // 5. Personalized Souvenirs
        souvenirsListBox.getChildren().clear();
        for (TripBriefing.SouvenirIdea souvenir : orEmptyList(briefing.souvenirs())) {
            Participant p = souvenir.userId() == null ? null : participantById.get(souvenir.userId());
            String travelerName = p != null ? p.shortName() : ("Traveler #" + (souvenir.userId() == null ? "1" : souvenir.userId()));

            Label travelerBadge = new Label(travelerName);
            travelerBadge.getStyleClass().addAll("badge", "badge-teal");

            Label costBadge = new Label(souvenir.estimatedCostUsd() == null ? "" : ("~" + money(souvenir.estimatedCostUsd())));
            costBadge.getStyleClass().addAll("badge", "badge-success");
            costBadge.setVisible(souvenir.estimatedCostUsd() != null);
            costBadge.setManaged(souvenir.estimatedCostUsd() != null);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, travelerBadge, spacer, costBadge);
            top.setAlignment(Pos.CENTER_LEFT);

            Label ideaLabel = wrapped(orEmpty(souvenir.idea()), "item-title");

            VBox box = new VBox(8, top, ideaLabel);
            box.getStyleClass().add("list-row");
            souvenirsListBox.getChildren().add(box);
        }

        // 6. Historical Facts
        factsListBox.getChildren().clear();
        List<String> facts = orEmptyList(briefing.historicalFacts());
        for (int i = 0; i < facts.size(); i++) {
            Label badge = new Label("FACT " + (i + 1));
            badge.getStyleClass().addAll("badge", "badge-neutral");
            Label factText = wrapped(facts.get(i), "body-text");
            VBox box = new VBox(6, badge, factText);
            box.getStyleClass().add("list-row");
            factsListBox.getChildren().add(box);
        }
    }

    private void updatePackingProgress() {
        if (packingProgressBadge == null) return;
        int total = packingCheckBoxes.size();
        long packed = packingCheckBoxes.stream().filter(CheckBox::isSelected).count();
        packingProgressBadge.setText(packed + " / " + total + " packed");
        UiTheme.setBadgeVariant(packingProgressBadge,
                (total > 0 && packed == total) ? "badge-success" : (packed > 0 ? "badge-teal" : "badge-neutral"));
    }

    // ---------- Navigation ----------

    @FXML public void goToPreferences(ActionEvent event) { NavigationManager.getInstance().navigateTo("preferences"); }
    @FXML public void goToSharedTrip(ActionEvent event) { NavigationManager.getInstance().navigateTo("trips"); }

    // ---------- Helpers ----------

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String money(BigDecimal amount) {
        if (amount == null) return "$?";
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        format.setMinimumFractionDigits(whole ? 0 : 2);
        format.setMaximumFractionDigits(whole ? 0 : 2);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static <T> List<T> orEmptyList(List<T> list) {
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
