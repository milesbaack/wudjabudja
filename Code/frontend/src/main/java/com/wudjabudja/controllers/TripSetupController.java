package com.wudjabudja.controllers;

import com.wudjabudja.models.Trip;
import com.wudjabudja.models.TripRequest;
import com.wudjabudja.models.TripType;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.TripService;
import com.wudjabudja.session.AppSession;
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
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

    private static final int MAX_DAYS = 7;

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
        if (createTripBtn != null) {
            createTripBtn.setGraphic(Icons.create(Icons.ARROW_RIGHT, 15, "#FFFFFF"));
        }

        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTripLength());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTripLength());

        String flash = AppSession.takeFlashMessage();
        if (flash != null) showStatus(flash, AppSession.isLastFlashError());

        showCurrentTrip();
        loadTrips();
    }

    @FXML
    public void handleCreateTrip(ActionEvent event) {
        TripRequest request = buildRequest();
        if (request == null) return;

        setBusy(true);
        showStatus("Creating your trip...", false);

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
                NavigationManager.getInstance().refreshSidebar();
                NavigationManager.getInstance().navigateTo("preferences");
            } else {
                showStatus(result.message(), true);
            }
        });
        task.setOnFailed(e -> {
            setBusy(false);
            showStatus("Something went wrong creating the trip: " + task.getException().getMessage(), true);
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

    // ---------- form checks ----------

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
            showStatus("Please fix the highlighted fields and try again.", true);
            return null;
        }
        return new TripRequest(null, destination, start.toString(), end.toString(), type.getValue(), budget);
    }

    private LocalDate readDate(DatePicker picker) {
        String typed = picker.getEditor().getText();
        if (typed != null && !typed.isBlank()) {
            try {
                LocalDate date = picker.getConverter().fromString(typed.trim());
                picker.setValue(date);
                return date;
            } catch (RuntimeException e) {
                return null;
            }
        }
        return picker.getValue();
    }

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
            tripLengthLabel.setVisible(false);
            tripLengthLabel.setManaged(false);
            return;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        tripLengthLabel.setText(days + (days == 1 ? " day" : " days"));
        tripLengthLabel.setVisible(true);
        tripLengthLabel.setManaged(true);
        UiTheme.setBadgeVariant(tripLengthLabel, days > MAX_DAYS ? "badge-danger" : "badge-teal");
    }

    // ---------- current trip + "Your trips" ----------

    private void showCurrentTrip() {
        Trip trip = AppSession.getCurrentTrip();
        boolean has = trip != null;
        currentTripBox.setVisible(has);
        currentTripBox.setManaged(has);
        if (has) currentTripLabel.setText(trip.title() + " · " + trip.dateRangeText());
    }

    private void loadTrips() {
        Task<ApiResult<List<Trip>>> task = new Task<>() {
            @Override
            protected ApiResult<List<Trip>> call() {
                return tripService.listTrips();
            }
        };
        task.setOnSucceeded(e -> showTrips(task.getValue()));
        task.setOnFailed(e -> tripsMessageLabel.setText("Couldn't load your saved trips."));
        startInBackground(task);
    }

    private void showTrips(ApiResult<List<Trip>> result) {
        tripsList.getChildren().clear();
        if (!result.success()) {
            tripsMessageLabel.setText(result.message());
            tripsMessageLabel.getStyleClass().removeAll("meta-text", "field-error");
            tripsMessageLabel.getStyleClass().add("field-error");
            return;
        }
        tripsMessageLabel.getStyleClass().removeAll("field-error");
        if (!tripsMessageLabel.getStyleClass().contains("meta-text")) {
            tripsMessageLabel.getStyleClass().add("meta-text");
        }
        List<Trip> trips = result.data();
        tripsMessageLabel.setText(trips.isEmpty()
                ? "No trips yet. Create your first trip above to get started."
                : "Jump right back into an existing trip:");
        for (Trip trip : trips) {
            tripsList.getChildren().add(tripRow(trip));
        }
    }

    private HBox tripRow(Trip trip) {
        boolean isCurrent = AppSession.getTripId() != null && AppSession.getTripId().equals(trip.id());

        Label title = new Label(trip.title());
        title.getStyleClass().add("item-title");

        Label typeBadge = new Label(TripType.labelFor(trip.tripType()));
        typeBadge.getStyleClass().addAll("badge", "badge-neutral");

        HBox titleLine = new HBox(8, title, typeBadge);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        if (isCurrent) {
            Label activeBadge = new Label("ACTIVE");
            activeBadge.getStyleClass().addAll("badge", "badge-teal");
            titleLine.getChildren().add(activeBadge);
        }

        String budgetStr = trip.totalBudget() != null
                ? "$" + trip.totalBudget().stripTrailingZeros().toPlainString() + " budget"
                : "Budget TBD";
        Label details = new Label(trip.destination() + "  ·  " + trip.dateRangeText()
                + " (" + trip.numDays() + (trip.numDays() == 1 ? " day)" : " days)")
                + "  ·  " + budgetStr);
        details.getStyleClass().add("meta-text");

        VBox text = new VBox(4, titleLine, details);

        Button open = new Button("Open Trip");
        open.getStyleClass().addAll(isCurrent ? "btn-primary" : "btn-soft", "btn-sm");
        open.setOnAction(e -> {
            AppSession.setCurrentTrip(trip);
            NavigationManager.getInstance().refreshSidebar();
            NavigationManager.getInstance().navigateTo("itinerary");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(14, Icons.create(Icons.MAP_PIN, 18, isCurrent ? "#0D9488" : "#64748B"), text, spacer, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(isCurrent ? "list-row-active" : "list-row");
        return row;
    }

    // ---------- small UI helpers ----------

    private void setBusy(boolean busy) {
        createTripBtn.setDisable(busy);
        createSpinner.setVisible(busy);
        createSpinner.setManaged(busy);
    }

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
        UiTheme.showStatus(statusLabel, message, isError);
    }

    private static void startInBackground(Task<?> task) {
        Thread thread = new Thread(task, "trip-setup-request");
        thread.setDaemon(true);
        thread.start();
    }
}
