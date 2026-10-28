package com.wudjabudja.controllers;

import com.wudjabudja.models.PreferenceRequest;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.PreferenceService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

/**
 * Controller for the Onboarding & Travel Preference Quiz View (MVP-08).
 * Captures traveler constraints: Wake-up Time, Experience Level, Max Radius,
 * Categorical Budgets, and Activity Interests.
 * Communicates directly with Spring Boot REST endpoints for Save and Load.
 */
public class PreferenceController implements Initializable {

    @FXML
    private ComboBox<String> wakeTimeCombo;

    @FXML
    private ToggleGroup experienceGroup;

    @FXML
    private RadioButton noviceRadio;

    @FXML
    private RadioButton intermediateRadio;

    @FXML
    private RadioButton expertRadio;

    @FXML
    private Slider radiusSlider;

    @FXML
    private Label radiusValueLabel;

    @FXML
    private Slider transitSlider;

    @FXML
    private Label transitValueLabel;

    @FXML
    private Slider lodgingSlider;

    @FXML
    private Label lodgingValueLabel;

    @FXML
    private Slider foodSlider;

    @FXML
    private Label foodValueLabel;

    @FXML
    private Slider activitiesSlider;

    @FXML
    private Label activitiesValueLabel;

    @FXML
    private Slider perBlockCapSlider;

    @FXML
    private Label perBlockCapLabel;

    @FXML
    private Label totalBudgetLabel;

    @FXML
    private CheckBox interestFood;

    @FXML
    private CheckBox interestCulture;

    @FXML
    private CheckBox interestOutdoors;

    @FXML
    private CheckBox interestNightlife;

    @FXML
    private CheckBox interestShopping;

    @FXML
    private CheckBox interestRelaxation;

    @FXML
    private CheckBox interestSports;

    @FXML
    private CheckBox interestTour;

    @FXML
    private Label statusLabel;

    private final PreferenceService preferenceService = new PreferenceService();

    // Default test IDs for MVP Sprint 1 scaffolding
    private static final Long DEFAULT_TRIP_ID = 1L;
    private static final Long DEFAULT_USER_ID = 1L;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Populate Wake-Up Times
        List<String> times = Arrays.asList(
                "06:00 AM", "06:30 AM", "07:00 AM", "07:30 AM",
                "08:00 AM", "08:30 AM", "09:00 AM", "09:30 AM",
                "10:00 AM", "10:30 AM", "11:00 AM"
        );
        wakeTimeCombo.setItems(FXCollections.observableArrayList(times));
        wakeTimeCombo.setValue("08:30 AM");

        // 2. Dynamic Slider Listeners for Live Updates
        radiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int miles = newVal.intValue();
            radiusValueLabel.setText(miles + " miles");
        });

        transitSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            transitValueLabel.setText("$" + newVal.intValue());
            updateTotalBudget();
        });

        lodgingSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            lodgingValueLabel.setText("$" + newVal.intValue());
            updateTotalBudget();
        });

        foodSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            foodValueLabel.setText("$" + newVal.intValue());
            updateTotalBudget();
        });

        activitiesSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            activitiesValueLabel.setText("$" + newVal.intValue());
            updateTotalBudget();
        });

        perBlockCapSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            perBlockCapLabel.setText("$" + newVal.intValue() + " / activity");
        });

        // Load existing preferences if cached in-memory
        PreferenceRequest existing = PreferenceService.getCurrentPreferences();
        if (existing != null) {
            populateFromRequest(existing);
        } else {
            resetDefaults();
        }

        updateTotalBudget();
    }

    private void updateTotalBudget() {
        int total = (int) (transitSlider.getValue() + lodgingSlider.getValue() +
                foodSlider.getValue() + activitiesSlider.getValue());
        if (totalBudgetLabel != null) {
            totalBudgetLabel.setText("$" + total + " total estimated per person");
        }
    }

    /**
     * Builds a PreferenceRequest from current UI controls.
     * Returns null if validation fails.
     */
    private PreferenceRequest buildRequestFromInputs() {
        String timeStr = wakeTimeCombo.getValue();
        if (timeStr == null || timeStr.trim().isEmpty()) {
            showStatus("Please select a preferred wake-up time.", true);
            return null;
        }

        String formattedWakeTime = convertTo24Hour(timeStr);

        String experience = "intermediate";
        if (noviceRadio.isSelected()) experience = "novice";
        else if (expertRadio.isSelected()) experience = "expert";

        int maxRadius = (int) radiusSlider.getValue();

        Map<String, Double> categoricalBudgets = new LinkedHashMap<>();
        categoricalBudgets.put("transit", Math.round(transitSlider.getValue() * 100.0) / 100.0);
        categoricalBudgets.put("lodging", Math.round(lodgingSlider.getValue() * 100.0) / 100.0);
        categoricalBudgets.put("food", Math.round(foodSlider.getValue() * 100.0) / 100.0);
        categoricalBudgets.put("activities", Math.round(activitiesSlider.getValue() * 100.0) / 100.0);

        BigDecimal perBlockCap = BigDecimal.valueOf((long) perBlockCapSlider.getValue());

        List<String> interests = new ArrayList<>();
        if (interestFood.isSelected()) interests.add("food");
        if (interestCulture.isSelected()) interests.add("culture");
        if (interestOutdoors.isSelected()) interests.add("outdoors");
        if (interestNightlife.isSelected()) interests.add("nightlife");
        if (interestShopping.isSelected()) interests.add("shopping");
        if (interestRelaxation.isSelected()) interests.add("relaxation");
        if (interestSports.isSelected()) interests.add("sports");
        if (interestTour.isSelected()) interests.add("tour");

        if (interests.isEmpty()) {
            showStatus("Please select at least one interest.", true);
            return null;
        }

        return new PreferenceRequest(
                DEFAULT_TRIP_ID,
                DEFAULT_USER_ID,
                formattedWakeTime,
                experience,
                maxRadius,
                categoricalBudgets,
                perBlockCap,
                interests
        );
    }

    /**
     * Handles the Save button: sends POST /api/trips/{id}/preferences to Spring Boot.
     * Displays a clear failure status if saving fails.
     */
    @FXML
    public void handleSavePreferences(ActionEvent event) {
        PreferenceRequest request = buildRequestFromInputs();
        if (request == null) return;

        PreferenceService.SaveResult result = preferenceService.savePreferences(request);
        if (result.isSuccess()) {
            showStatus("✓ " + result.getMessage(), false);
        } else {
            showStatus("✗ " + result.getMessage(), true);
        }
    }

    /**
     * Handles the Load button: sends GET /api/trips/{id}/preferences to Spring Boot.
     * Populates all inputs with the server's stored preferences.
     */
    @FXML
    public void handleLoadPreferences(ActionEvent event) {
        showStatus("Loading preferences from server...", false);
        PreferenceService.LoadResult result = preferenceService.loadPreferences(DEFAULT_TRIP_ID, DEFAULT_USER_ID);

        if (result.isSuccess() && result.getPreferences() != null) {
            populateFromRequest(result.getPreferences());
            showStatus("✓ " + result.getMessage(), false);
        } else {
            showStatus("✗ " + result.getMessage(), true);
        }
    }

    /**
     * Handles the "Save & Continue to Dashboard" button in the onboarding quiz flow.
     * Saves preferences, and upon success navigates immediately to the Dashboard.
     */
    @FXML
    public void handleSaveAndContinue(ActionEvent event) {
        PreferenceRequest request = buildRequestFromInputs();
        if (request == null) return;

        PreferenceService.SaveResult result = preferenceService.savePreferences(request);
        if (result.isSuccess()) {
            showStatus("✓ Preferences saved! Taking you to the dashboard...", false);
            NavigationManager.getInstance().navigateTo("dashboard");
        } else {
            // Stay on the quiz and highlight the failure
            showStatus("✗ " + result.getMessage(), true);
        }
    }

    @FXML
    public void handleReset(ActionEvent event) {
        resetDefaults();
        showStatus("Preferences reset to defaults.", false);
    }

    private void resetDefaults() {
        wakeTimeCombo.setValue("08:30 AM");
        intermediateRadio.setSelected(true);
        radiusSlider.setValue(15);
        radiusValueLabel.setText("15 miles");

        transitSlider.setValue(60);
        transitValueLabel.setText("$60");

        lodgingSlider.setValue(300);
        lodgingValueLabel.setText("$300");

        foodSlider.setValue(200);
        foodValueLabel.setText("$200");

        activitiesSlider.setValue(150);
        activitiesValueLabel.setText("$150");

        perBlockCapSlider.setValue(35);
        perBlockCapLabel.setText("$35 / activity");

        interestFood.setSelected(true);
        interestCulture.setSelected(true);
        interestOutdoors.setSelected(false);
        interestNightlife.setSelected(true);
        interestShopping.setSelected(false);
        interestRelaxation.setSelected(false);
        interestSports.setSelected(true);
        interestTour.setSelected(false);

        updateTotalBudget();
        clearStatus();
    }

    private void populateFromRequest(PreferenceRequest req) {
        if (req.getWakeTime() != null) {
            wakeTimeCombo.setValue(convertToDisplayTime(req.getWakeTime()));
        }
        if ("novice".equalsIgnoreCase(req.getExperienceLevel())) noviceRadio.setSelected(true);
        else if ("expert".equalsIgnoreCase(req.getExperienceLevel())) expertRadio.setSelected(true);
        else intermediateRadio.setSelected(true);

        if (req.getMaxRadiusMiles() != null) {
            radiusSlider.setValue(req.getMaxRadiusMiles());
            radiusValueLabel.setText(req.getMaxRadiusMiles() + " miles");
        }

        if (req.getCategoricalBudgets() != null) {
            Map<String, Double> b = req.getCategoricalBudgets();
            if (b.containsKey("transit")) {
                transitSlider.setValue(b.get("transit"));
                transitValueLabel.setText("$" + b.get("transit").intValue());
            }
            if (b.containsKey("lodging")) {
                lodgingSlider.setValue(b.get("lodging"));
                lodgingValueLabel.setText("$" + b.get("lodging").intValue());
            }
            if (b.containsKey("food")) {
                foodSlider.setValue(b.get("food"));
                foodValueLabel.setText("$" + b.get("food").intValue());
            }
            if (b.containsKey("activities")) {
                activitiesSlider.setValue(b.get("activities"));
                activitiesValueLabel.setText("$" + b.get("activities").intValue());
            }
        }

        if (req.getPerBlockCapUsd() != null) {
            perBlockCapSlider.setValue(req.getPerBlockCapUsd().doubleValue());
            perBlockCapLabel.setText("$" + req.getPerBlockCapUsd().intValue() + " / activity");
        }

        if (req.getInterests() != null) {
            List<String> ints = req.getInterests();
            interestFood.setSelected(ints.contains("food"));
            interestCulture.setSelected(ints.contains("culture"));
            interestOutdoors.setSelected(ints.contains("outdoors"));
            interestNightlife.setSelected(ints.contains("nightlife"));
            interestShopping.setSelected(ints.contains("shopping"));
            interestRelaxation.setSelected(ints.contains("relaxation"));
            interestSports.setSelected(ints.contains("sports"));
            interestTour.setSelected(ints.contains("tour"));
        }

        updateTotalBudget();
    }

    private String convertTo24Hour(String displayTime) {
        try {
            boolean isPm = displayTime.toUpperCase().contains("PM");
            String clean = displayTime.replaceAll("[^0-9:]", "").trim();
            String[] parts = clean.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            if (isPm && hour < 12) hour += 12;
            if (!isPm && hour == 12) hour = 0;
            return String.format("%02d:%02d", hour, min);
        } catch (Exception e) {
            return "08:30";
        }
    }

    private String convertToDisplayTime(String time24) {
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            String period = hour >= 12 ? "PM" : "AM";
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12;
            return String.format("%02d:%02d %s", displayHour, min, period);
        } catch (Exception e) {
            return "08:30 AM";
        }
    }

    private void showStatus(String message, boolean isError) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            if (isError) {
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            } else {
                statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
            }
        }
    }

    private void clearStatus() {
        if (statusLabel != null) {
            statusLabel.setText("");
        }
    }
}
