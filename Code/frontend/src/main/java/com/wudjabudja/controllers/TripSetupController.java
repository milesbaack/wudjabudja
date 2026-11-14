package com.wudjabudja.controllers;

import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripRequest;
import com.wudjabudja.models.TripType;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.DemoTripSupport;
import com.wudjabudja.services.TripService;
import com.wudjabudja.session.AppSession;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Trip Setup screen: destination, dates, trip type and budget -> POST /api/trips -> quiz.
 * Checks the same rules as the backend's TripService, so most mistakes show up before any request.
 */
public class TripSetupController implements Initializable {

    private static final int MAX_DAYS = 7;   // same limit as the backend

    @FXML private HBox currentTripBox;
    @FXML private Label currentTripLabel;

    @FXML private TextField destinationField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Label tripLengthLabel;
    @FXML private ComboBox<TripType> tripTypeCombo;
    @FXML private TextField budgetField;

    @FXML private Label destinationError;
    @FXML private Label datesError;
    @FXML private Label tripTypeError;
    @FXML private Label budgetError;

    @FXML private Button createTripBtn;
    @FXML private ProgressIndicator createSpinner;
    @FXML private Label statusLabel;

    @FXML private Label tripsMessageLabel;
    @FXML private VBox tripsList;

    private final TripService tripService = new TripService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tripTypeCombo.getItems().setAll(TripType.values());

        // Show "3 days" next to the dates as soon as both are picked
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTripLength());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTripLength());

        // e.g. "Create a trip first" when the quiz sent us here
        String flash = AppSession.takeFlashMessage();
        if (flash != null) showStatus(flash, true);

        showCurrentTrip();
        loadTrips();
    }

    @FXML
    public void handleCreateTrip(ActionEvent event) {
        TripRequest request = buildRequest();
        if (request == null) return;   // field errors are already showing

        setBusy(true);
        showStatus("Creating your trip...", false);

        // Off the UI thread, so the window doesn't freeze if the backend is slow or down
        Task<ApiResult<Trip>> task = new Task<>() {
            @Override
            protected ApiResult<Trip> call() {
                return tripService.createTrip(request);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            ApiResult<Trip> result = task.getValue();
            if (result.success()) {
                AppSession.setCurrentTrip(result.data());
                NavigationManager.getInstance().navigateTo("preferences");
            } else {
                showStatus("✗ " + result.message(), true);
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            showStatus("✗ Something went wrong creating the trip: " + task.getException().getMessage(), true);
        });
        startInBackground(task);
    }

    @FXML
    public void goToQuiz(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("preferences");
    }

    @FXML
    public void goToSharedTrip(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("trips");
    }

    @FXML
    public void goToItinerary(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("itinerary");
    }

    @FXML
    public void handleLoadDemoTrip(ActionEvent event) {
        Trip demo = DemoTripSupport.activateDemoGroupTrip();
        AppSession.setCurrentTrip(demo);
        AppSession.setFlashMessage("✓ Loaded Demo Group Trip! Try adding a traveler by email, editing quizzes, or jumping to Group Voting.");
        NavigationManager.getInstance().navigateTo("trips");
    }

    // ---------- form checks ----------

    // Returns the request, or null after showing what's wrong next to each field
    private TripRequest buildRequest() {
        clearErrors();
        boolean ok = true;

        String destination = destinationField.getText() == null ? "" : destinationField.getText().trim();
        if (destination.isEmpty()) {
            ok = showFieldError(destinationError, "Enter where you're going.");
        } else if (destination.length() > 255) {
            ok = showFieldError(destinationError, "That's too long (255 characters max).");
        }

        LocalDate start = readDate(startDatePicker);
        LocalDate end = readDate(endDatePicker);
        if (start == null || end == null) {
            ok = showFieldError(datesError, "Pick a start and end date (or type them like 3/12/2027).");
        } else if (end.isBefore(start)) {
            ok = showFieldError(datesError, "The end date can't be before the start date.");
        } else if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            ok = showFieldError(datesError, "Trips can be at most " + MAX_DAYS + " days for now.");
        }

        TripType type = tripTypeCombo.getValue();
        if (type == null) {
            ok = showFieldError(tripTypeError, "Choose a trip type.");
        }

        BigDecimal budget = parseBudget(budgetField.getText());
        if (budget == null) {
            ok = showFieldError(budgetError, "Enter the total budget as a number, like 2400.");
        } else if (budget.signum() <= 0) {
            ok = showFieldError(budgetError, "The budget has to be more than $0.");
        }

        if (!ok) {
            showStatus("Fix the highlighted fields and try again.", true);
            return null;
        }
        return new TripRequest(null, destination, start.toString(), end.toString(), type.getValue(), budget);
    }

    // DatePicker only saves typed text when you press Enter, so read the text box ourselves too
    private LocalDate readDate(DatePicker picker) {
        String typed = picker.getEditor().getText();
        if (typed != null && !typed.isBlank()) {
            try {
                LocalDate date = picker.getConverter().fromString(typed.trim());
                picker.setValue(date);
                return date;
            } catch (RuntimeException e) {
                return null;   // unreadable text counts as missing
            }
        }
        return picker.getValue();
    }

    // "$2,400" or "2400.50" -> a number. null if it isn't one.
    private static BigDecimal parseBudget(String text) {
        if (text == null) return null;
        String clean = text.replace("$", "").replace(",", "").trim();
        if (clean.isEmpty()) return null;
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateTripLength() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null || end.isBefore(start)) {
            tripLengthLabel.setText("");
            return;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        tripLengthLabel.setText(days + (days == 1 ? " day" : " days"));
        tripLengthLabel.setStyle(days > MAX_DAYS ? "-fx-text-fill: #dc2626;" : "-fx-text-fill: #2563eb;");
    }

    // ---------- current trip + "Your trips" ----------

    private void showCurrentTrip() {
        Trip trip = AppSession.getCurrentTrip();
        boolean has = trip != null;
        currentTripBox.setVisible(has);
        currentTripBox.setManaged(has);
        if (has) currentTripLabel.setText("Working on: " + trip.title());
    }

    private void loadTrips() {
        Task<ApiResult<List<Trip>>> task = new Task<>() {
            @Override
            protected ApiResult<List<Trip>> call() {
                return tripService.listTrips();
            }
        };
        task.setOnSucceeded(e -> showTrips(task.getValue()));
        task.setOnFailed(e -> tripsMessageLabel.setText("Couldn't load your trips."));
        startInBackground(task);
    }

    private void showTrips(ApiResult<List<Trip>> result) {
        tripsList.getChildren().clear();
        if (!result.success()) {
            tripsMessageLabel.setText(result.message());
            tripsMessageLabel.setStyle("-fx-text-fill: #dc2626;");
            return;
        }
        List<Trip> trips = result.data();
        tripsMessageLabel.setText(trips.isEmpty() ? "No trips yet. Create your first one above." : "Pick up where you left off:");
        for (Trip trip : trips) {
            tripsList.getChildren().add(tripRow(trip));
        }
    }

    private HBox tripRow(Trip trip) {
        Label title = new Label(trip.title());
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
        Label details = new Label(TripType.labelFor(trip.tripType()) + " · " + trip.numDays() + " days · $"
                + trip.totalBudget().stripTrailingZeros().toPlainString() + " budget");
        details.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        VBox text = new VBox(2, title, details);

        Button open = new Button("Open");
        open.setStyle("-fx-background-color: white; -fx-border-color: #2563eb; -fx-border-radius: 8; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-padding: 5 14; -fx-cursor: hand;");
        open.setOnAction(e -> {
            AppSession.setCurrentTrip(trip);
            NavigationManager.getInstance().navigateTo("itinerary");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, text, spacer, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14;");
        return row;
    }

    // ---------- small UI helpers ----------

    private void setBusy(boolean busy) {
        createTripBtn.setDisable(busy);
        createSpinner.setVisible(busy);
    }

    // Always returns false, so callers can write: ok = showFieldError(...)
    private boolean showFieldError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
        return false;
    }

    private void clearErrors() {
        for (Label label : List.of(destinationError, datesError, tripTypeError, budgetError)) {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;"
                : "-fx-text-fill: #059669; -fx-font-weight: bold;");
    }

    private static void startInBackground(Task<?> task) {
        Thread thread = new Thread(task, "trip-setup-request");
        thread.setDaemon(true);   // don't keep the app alive after the window closes
        thread.start();
    }
}
