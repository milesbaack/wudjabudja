package com.wudjabudja.controllers;

import com.wudjabudja.models.Itinerary;
import com.wudjabudja.models.Participant;
import com.wudjabudja.models.PreferenceRequest;
import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripBriefing;
import com.wudjabudja.models.TripType;
import com.wudjabudja.models.VoteWinner;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.BriefingService;
import com.wudjabudja.services.ItineraryService;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.PreferenceService;
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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Live Trip Dashboard view.
 * Displays live trip metadata, traveler quiz readiness, active traveler's saved preferences,
 * Borda Count winners, and Trip Briefing highlights.
 */
public class DashboardController implements Initializable {

    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    @FXML private Label greetingLabel;
    @FXML private Button refreshBtn;
    @FXML private Button newTripBtn;
    @FXML private HBox busyBox;
    @FXML private Label busyLabel;
    @FXML private VBox noTripBox;
    @FXML private VBox activeTripContentBox;

    // Hero Banner Controls
    @FXML private Label joinCodeLabel;
    @FXML private Label countdownLabel;
    @FXML private Label tripTitleLabel;
    @FXML private Label tripSubtitleLabel;
    @FXML private Label statBudgetLabel;
    @FXML private Label statTripTypeLabel;
    @FXML private Label statItineraryStatusLabel;
    @FXML private Label readinessTextLabel;
    @FXML private Label readinessPercentLabel;
    @FXML private ProgressBar readinessProgressBar;

    // Left Card: Travel Profile Controls
    @FXML private Label profileCardTitleLabel;
    @FXML private Label profileStatusBadge;
    @FXML private Label prefWakeTimeLabel;
    @FXML private Label prefExperienceLabel;
    @FXML private Label prefRadiusLabel;
    @FXML private Label prefCapLabel;
    @FXML private Label prefInterestsLabel;

    // Right Card: Voting & Itinerary Controls
    @FXML private Label votingStatusBadge;
    @FXML private Label votingSubtitleLabel;
    @FXML private VBox votingPreviewBox;

    // Bottom Card: Briefing Highlight Controls
    @FXML private StackPane briefingIconBox;
    @FXML private Label briefingHighlightTitle;
    @FXML private Label briefingHighlightText;

    private final TripService tripService = new TripService();
    private final ParticipantService participantService = new ParticipantService();
    private final PreferenceService preferenceService = new PreferenceService();
    private final ItineraryService itineraryService = new ItineraryService();
    private final VoteService voteService = new VoteService();
    private final BriefingService briefingService = new BriefingService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (refreshBtn != null) {
            refreshBtn.setGraphic(Icons.create(Icons.REFRESH, 14, "#334155"));
        }
        if (newTripBtn != null) {
            newTripBtn.setGraphic(Icons.create(Icons.PLUS, 15, "#FFFFFF"));
        }
        if (briefingIconBox != null) {
            briefingIconBox.getChildren().setAll(Icons.create(Icons.BOOK, 22, "#0D9488"));
        }
        updateGreeting();
        loadDashboardData();
    }

    private void updateGreeting() {
        Participant active = AppSession.getActiveTraveler();
        greetingLabel.setText("Welcome back, " + active.shortName());
    }

    private record DashboardSnapshot(
            Trip trip,
            List<Participant> participants,
            PreferenceRequest activePreferences,
            Itinerary itinerary,
            List<VoteWinner> winners,
            TripBriefing briefing
    ) {}

    private void loadDashboardData() {
        setBusy(true, "Loading live trip dashboard...");
        Long activeUserId = AppSession.getActiveUserId();

        Task<DashboardSnapshot> task = new Task<>() {
            @Override
            protected DashboardSnapshot call() {
                Trip activeTrip = AppSession.getCurrentTrip();
                if (activeTrip == null) {
                    ApiResult<List<Trip>> listRes = tripService.listTrips();
                    if (listRes.success() && listRes.data() != null && !listRes.data().isEmpty()) {
                        activeTrip = listRes.data().get(0);
                    } else {
                        return new DashboardSnapshot(null, List.of(), null, null, List.of(), null);
                    }
                } else {
                    ApiResult<Trip> freshTrip = tripService.getTrip(activeTrip.id());
                    if (freshTrip.success() && freshTrip.data() != null) {
                        activeTrip = freshTrip.data();
                    }
                }

                Long tripId = activeTrip.id();
                ApiResult<List<Participant>> pRes = participantService.listParticipants(tripId);
                List<Participant> roster = (pRes.success() && pRes.data() != null && !pRes.data().isEmpty())
                        ? pRes.data()
                        : List.of(AppSession.defaultTraveler());

                PreferenceService.LoadResult prefRes = preferenceService.loadPreferences(tripId, activeUserId);
                PreferenceRequest prefs = prefRes.isSuccess() ? prefRes.getPreferences() : null;

                ApiResult<Itinerary> itRes = itineraryService.loadItinerary(tripId);
                Itinerary itinerary = (itRes.success() && itRes.data() != null) ? itRes.data() : null;

                ApiResult<List<VoteWinner>> winRes = voteService.loadWinners(tripId);
                List<VoteWinner> winners = (winRes.success() && winRes.data() != null) ? winRes.data() : List.of();

                ApiResult<TripBriefing> briefRes = briefingService.loadBriefing(tripId);
                TripBriefing briefing = (briefRes.success() && briefRes.data() != null) ? briefRes.data() : null;

                return new DashboardSnapshot(activeTrip, roster, prefs, itinerary, winners, briefing);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null);
            renderDashboard(task.getValue());
        });

        task.setOnFailed(e -> {
            setBusy(false, null);
            renderDashboard(new DashboardSnapshot(AppSession.getCurrentTrip(), List.of(), null, null, List.of(), null));
        });

        Thread thread = new Thread(task, "dashboard-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderDashboard(DashboardSnapshot snap) {
        if (snap.trip() == null) {
            activeTripContentBox.setVisible(false);
            activeTripContentBox.setManaged(false);
            noTripBox.setVisible(true);
            noTripBox.setManaged(true);
            return;
        }

        AppSession.setCurrentTrip(snap.trip());
        NavigationManager.getInstance().refreshSidebar();
        noTripBox.setVisible(false);
        noTripBox.setManaged(false);
        activeTripContentBox.setVisible(true);
        activeTripContentBox.setManaged(true);

        Trip trip = snap.trip();
        List<Participant> roster = snap.participants() == null || snap.participants().isEmpty()
                ? List.of(AppSession.defaultTraveler())
                : snap.participants();

        for (Participant p : roster) {
            if (p.userId() != null && p.userId().equals(AppSession.getActiveUserId())) {
                AppSession.setActiveTraveler(p);
                break;
            }
        }
        updateGreeting();

        // 1. Hero Card
        joinCodeLabel.setText("Join Code: " + (trip.joinCode() != null && !trip.joinCode().isBlank()
                ? trip.joinCode()
                : "TRIP-" + trip.id()));
        countdownLabel.setText(countdownText(trip.startDate(), trip.endDate()));
        tripTitleLabel.setText(trip.title());

        String names = roster.stream().map(Participant::shortName).collect(Collectors.joining(", "));
        long numDays = trip.numDays();
        tripSubtitleLabel.setText(trip.destination()
                + "  ·  " + trip.dateRangeText()
                + "  ·  " + numDays + (numDays == 1 ? " Day" : " Days")
                + "  ·  " + roster.size() + (roster.size() == 1 ? " Traveler (" : " Travelers (") + names + ")");

        BigDecimal totalBudget = trip.totalBudget() != null ? trip.totalBudget() : BigDecimal.ZERO;
        BigDecimal perPerson = roster.isEmpty()
                ? totalBudget
                : totalBudget.divide(BigDecimal.valueOf(roster.size()), 0, RoundingMode.HALF_UP);
        statBudgetLabel.setText(money(totalBudget)
                + (roster.size() > 1 ? " (" + money(perPerson) + "/person)" : ""));
        statTripTypeLabel.setText(TripType.labelFor(trip.tripType()));

        int optionCount = countOptions(snap.itinerary());
        int finalOptionCount = countFinalOptions(snap.itinerary(), snap.winners());
        if (optionCount == 0) {
            statItineraryStatusLabel.setText("Not generated yet");
        } else if (finalOptionCount > 0) {
            statItineraryStatusLabel.setText(optionCount + " options · " + finalOptionCount + " winners");
        } else {
            statItineraryStatusLabel.setText(optionCount + " candidate options");
        }

        // Readiness progress bar
        long savedCount = roster.stream()
                .filter(p -> p.quizSaved() || (p.userId() != null && p.userId().equals(AppSession.getActiveUserId()) && snap.activePreferences() != null))
                .count();
        int totalCount = Math.max(1, roster.size());
        double ratio = Math.min(1.0, (double) savedCount / totalCount);
        int percent = (int) Math.round(ratio * 100);
        readinessTextLabel.setText(savedCount + " of " + totalCount
                + (totalCount == 1 ? " traveler has" : " travelers have")
                + " submitted preferences");
        readinessPercentLabel.setText(percent + "% Ready");
        readinessProgressBar.setProgress(ratio);

        // 2. Left Card: Active Traveler's Preferences
        Participant active = AppSession.getActiveTraveler();
        profileCardTitleLabel.setText("Travel Profile — " + active.shortName());
        PreferenceRequest prefs = snap.activePreferences();
        if (prefs != null) {
            profileStatusBadge.setText("Quiz Saved");
            UiTheme.setBadgeVariant(profileStatusBadge, "badge-success");
            prefWakeTimeLabel.setText(formatWakeTime(prefs.getWakeTime()));
            prefExperienceLabel.setText(capitalize(prefs.getExperienceLevel()));
            prefRadiusLabel.setText((prefs.getMaxRadiusMiles() != null ? prefs.getMaxRadiusMiles() : 15) + " miles from center");
            prefCapLabel.setText(prefs.getPerBlockCapUsd() != null ? money(prefs.getPerBlockCapUsd()) + " / activity" : "No cap set");
            List<String> interests = prefs.getInterests() == null ? List.of() : prefs.getInterests();
            prefInterestsLabel.setText(interests.isEmpty()
                    ? "None selected"
                    : interests.stream().map(DashboardController::capitalize).collect(Collectors.joining(", ")));
        } else {
            profileStatusBadge.setText("Quiz Pending");
            UiTheme.setBadgeVariant(profileStatusBadge, "badge-warning");
            prefWakeTimeLabel.setText("Not saved yet");
            prefExperienceLabel.setText("Not saved yet");
            prefRadiusLabel.setText("Not saved yet");
            prefCapLabel.setText("Not saved yet");
            prefInterestsLabel.setText("Take the 6-step quiz below");
        }

        // 3. Right Card: Voting & Itinerary Preview
        renderVotingPreview(snap.itinerary(), snap.winners(), optionCount, finalOptionCount);

        // 4. Bottom Card: Trip Briefing Highlight
        renderBriefingHighlight(trip, snap.briefing());
    }

    private void renderVotingPreview(Itinerary itinerary, List<VoteWinner> winners, int optionCount, int finalCount) {
        votingPreviewBox.getChildren().clear();

        if (finalCount > 0) {
            votingStatusBadge.setText(finalCount + (finalCount == 1 ? " Winner" : " Winners"));
            UiTheme.setBadgeVariant(votingStatusBadge, "badge-amber");
            votingSubtitleLabel.setText("Borda Count consensus activities elected by the group:");

            List<PreviewItem> items = extractWinningPreviews(itinerary, winners);
            for (int i = 0; i < Math.min(3, items.size()); i++) {
                votingPreviewBox.getChildren().add(previewRow(items.get(i), true));
            }
        } else if (optionCount > 0) {
            votingStatusBadge.setText("Ready to Vote");
            UiTheme.setBadgeVariant(votingStatusBadge, "badge-teal");
            votingSubtitleLabel.setText("Candidate activities are ready. Head to Group Voting to rank 1st, 2nd, and 3rd choices:");

            List<PreviewItem> items = extractCandidatePreviews(itinerary);
            for (int i = 0; i < Math.min(3, items.size()); i++) {
                votingPreviewBox.getChildren().add(previewRow(items.get(i), false));
            }
        } else {
            votingStatusBadge.setText("Awaiting Itinerary");
            UiTheme.setBadgeVariant(votingStatusBadge, "badge-neutral");
            votingSubtitleLabel.setText("Once travelers save their quiz preferences, generate 3 candidate options per time block.");

            Label emptyHint = new Label("No itinerary options generated yet. Open \"View Itinerary\" or \"Shared Trip\" to generate options.");
            emptyHint.setWrapText(true);
            emptyHint.getStyleClass().addAll("well-plain", "meta-text");
            emptyHint.setMaxWidth(Double.MAX_VALUE);
            votingPreviewBox.getChildren().add(emptyHint);
        }
    }

    private record PreviewItem(String badgeText, String title, String subtitle) {}

    private HBox previewRow(PreviewItem item, boolean isWinner) {
        Label badge = new Label(item.badgeText());
        badge.getStyleClass().addAll("badge", isWinner ? "badge-amber" : "badge-neutral");

        Label title = new Label(item.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);

        Label sub = new Label(item.subtitle());
        sub.getStyleClass().add("meta-text");

        VBox text = new VBox(2.0, title, sub);
        HBox row = new HBox(12.0, badge, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(isWinner ? "winner-summary-row" : "list-row");
        return row;
    }

    private void renderBriefingHighlight(Trip trip, TripBriefing briefing) {
        briefingHighlightTitle.setText("Pre-Trip Briefing for " + trip.destination());
        if (briefing != null && briefing.customs() != null && !briefing.customs().isEmpty()) {
            TripBriefing.CustomTip first = briefing.customs().get(0);
            String tip = first.tip() != null ? first.tip() : "";
            String why = first.why() != null ? " — " + first.why() : "";
            briefingHighlightText.setText(tip + why);
        } else {
            briefingHighlightText.setText("Generate cultural customs, local phrases, a weather-aware packing checklist, and souvenir ideas for "
                    + trip.destination() + ".");
        }
    }

    // ---------- Navigation & Actions ----------

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadDashboardData();
    }

    @FXML
    public void goToTripSetup(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("tripsetup");
    }

    @FXML
    public void goToPreferences(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("preferences");
    }

    @FXML
    public void goToTrips(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("trips");
    }

    @FXML
    public void goToItinerary(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("itinerary");
    }

    @FXML
    public void goToVoting(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("voting");
    }

    @FXML
    public void goToBriefing(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("briefing");
    }

    // ---------- Helpers ----------

    private static int countOptions(Itinerary itinerary) {
        if (itinerary == null || itinerary.days() == null) return 0;
        int count = 0;
        for (Itinerary.Day d : itinerary.days()) {
            if (d.blocks() == null) continue;
            for (Itinerary.Block b : d.blocks()) {
                if (b.subgroups() == null) continue;
                for (Itinerary.Subgroup s : b.subgroups()) {
                    if (s.options() != null) count += s.options().size();
                }
            }
        }
        return count;
    }

    private static int countFinalOptions(Itinerary itinerary, List<VoteWinner> winners) {
        if (winners != null && !winners.isEmpty()) {
            return winners.size();
        }
        if (itinerary == null || itinerary.days() == null) return 0;
        int count = 0;
        for (Itinerary.Day d : itinerary.days()) {
            if (d.blocks() == null) continue;
            for (Itinerary.Block b : d.blocks()) {
                if (b.subgroups() == null) continue;
                for (Itinerary.Subgroup s : b.subgroups()) {
                    if (s.options() == null) continue;
                    for (Itinerary.Option o : s.options()) {
                        if (o.isFinal()) count++;
                    }
                }
            }
        }
        return count;
    }

    private static List<PreviewItem> extractWinningPreviews(Itinerary itinerary, List<VoteWinner> winners) {
        List<PreviewItem> list = new ArrayList<>();
        if (winners != null && !winners.isEmpty()) {
            for (VoteWinner w : winners) {
                String badge = shortDate(w.date()) + " " + capitalize(w.timeBlock());
                String sub = capitalize(w.category())
                        + (w.estimatedCostUsd() != null ? " · " + money(w.estimatedCostUsd()) : "")
                        + (w.totalPoints() != null ? " · " + w.totalPoints() + " Borda pts" : "");
                list.add(new PreviewItem(badge, w.title(), sub));
            }
            return list;
        }
        if (itinerary != null && itinerary.days() != null) {
            for (int i = 0; i < itinerary.days().size(); i++) {
                Itinerary.Day d = itinerary.days().get(i);
                if (d.blocks() == null) continue;
                for (Itinerary.Block b : d.blocks()) {
                    if (b.subgroups() == null) continue;
                    for (Itinerary.Subgroup s : b.subgroups()) {
                        if (s.options() == null) continue;
                        for (Itinerary.Option o : s.options()) {
                            if (o.isFinal()) {
                                list.add(new PreviewItem(
                                        "Day " + (i + 1) + " " + capitalize(b.timeBlock()),
                                        o.title(),
                                        capitalize(o.category()) + " · " + money(o.estimatedCostUsd())
                                ));
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    private static List<PreviewItem> extractCandidatePreviews(Itinerary itinerary) {
        List<PreviewItem> list = new ArrayList<>();
        if (itinerary == null || itinerary.days() == null) return list;
        for (int i = 0; i < itinerary.days().size(); i++) {
            Itinerary.Day d = itinerary.days().get(i);
            if (d.blocks() == null) continue;
            for (Itinerary.Block b : d.blocks()) {
                if (b.subgroups() == null) continue;
                for (Itinerary.Subgroup s : b.subgroups()) {
                    if (s.options() == null || s.options().isEmpty()) continue;
                    Itinerary.Option first = s.options().get(0);
                    list.add(new PreviewItem(
                            "Day " + (i + 1),
                            first.title(),
                            capitalize(b.timeBlock()) + " · " + capitalize(first.category()) + " · " + money(first.estimatedCostUsd())
                    ));
                }
            }
        }
        return list;
    }

    private static String countdownText(String startDateIso, String endDateIso) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate start = LocalDate.parse(startDateIso);
            LocalDate end = endDateIso != null ? LocalDate.parse(endDateIso) : start;
            if (today.isBefore(start)) {
                long days = ChronoUnit.DAYS.between(today, start);
                return days == 1 ? "Departing tomorrow" : "Departing in " + days + " days";
            } else if (!today.isAfter(end)) {
                return "Trip in progress!";
            } else {
                return "Trip completed";
            }
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String formatWakeTime(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return "8:30 AM";
        try {
            return LocalTime.parse(hhmm).format(TWELVE_HOUR);
        } catch (RuntimeException e) {
            return hhmm;
        }
    }

    private static String shortDate(String iso) {
        if (iso == null) return "";
        try {
            return LocalDate.parse(iso).format(SHORT_DATE);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.trim();
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }

    private static String money(BigDecimal amount) {
        if (amount == null) return "$0";
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        format.setMinimumFractionDigits(whole ? 0 : 2);
        format.setMaximumFractionDigits(whole ? 0 : 2);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private void setBusy(boolean busy, String message) {
        busyBox.setVisible(busy);
        busyBox.setManaged(busy);
        if (message != null) {
            busyLabel.setText(message);
        }
    }
}
