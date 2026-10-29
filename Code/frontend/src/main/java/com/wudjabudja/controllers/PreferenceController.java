package com.wudjabudja.controllers;

import com.wudjabudja.models.PreferenceRequest;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.PreferenceService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

/**
 * Controller for the Step-by-Step Onboarding Questionnaire (MVP-08).
 * Guides the user through a guided 6-step wizard:
 * 1. Morning Routine & Wake-Up Time
 * 2. Base Exploration Radius
 * 3. Travel Experience & Navigation Comfort
 * 4. Categorical Budget Allocation
 * 5. Interests & Activity Vibes
 * 6. Review & Submission
 */
public class PreferenceController implements Initializable {

    // Step Tracking & Header Controls
    @FXML private Label stepCounterLabel;
    @FXML private ProgressBar quizProgressBar;
    @FXML private Label stepHeadingLabel;

    // Step Panes
    @FXML private VBox step1Pane;
    @FXML private VBox step2Pane;
    @FXML private VBox step3Pane;
    @FXML private VBox step4Pane;
    @FXML private VBox step5Pane;
    @FXML private VBox step6Pane;

    // Bottom Navigation Buttons
    @FXML private Button prevBtn;
    @FXML private Button nextBtn;

    // Step 1: Wake Time Controls
    @FXML private ComboBox<String> wakeTimeCombo;

    // Step 2: Radius Controls
    @FXML private Slider radiusSlider;
    @FXML private Label radiusValueLabel;
    @FXML private Label radiusHintLabel;

    // Step 3: Experience Controls
    @FXML private ToggleGroup experienceGroup;
    @FXML private RadioButton noviceRadio;
    @FXML private RadioButton intermediateRadio;
    @FXML private RadioButton expertRadio;

    // Step 4: Budget Sliders & Labels
    @FXML private Slider transitSlider;
    @FXML private Label transitValueLabel;
    @FXML private Slider lodgingSlider;
    @FXML private Label lodgingValueLabel;
    @FXML private Slider foodSlider;
    @FXML private Label foodValueLabel;
    @FXML private Slider activitiesSlider;
    @FXML private Label activitiesValueLabel;
    @FXML private Slider perBlockCapSlider;
    @FXML private Label perBlockCapLabel;
    @FXML private Label totalBudgetLabel;
    @FXML private Label totalBreakdownLabel;

    // Step 5: Interests Checkboxes
    @FXML private CheckBox interestFood;
    @FXML private CheckBox interestCulture;
    @FXML private CheckBox interestOutdoors;
    @FXML private CheckBox interestNightlife;
    @FXML private CheckBox interestShopping;
    @FXML private CheckBox interestRelaxation;
    @FXML private CheckBox interestSports;
    @FXML private CheckBox interestTour;

    // Step 6: Review Summary Labels
    @FXML private Label summaryWakeTime;
    @FXML private Label summaryRadius;
    @FXML private Label summaryExperience;
    @FXML private Label summaryTotalBudget;
    @FXML private Label summaryPerBlockCap;
    @FXML private Label summaryInterests;

    // Status Label
    @FXML private Label statusLabel;

    private int currentStep = 1;
    private static final int TOTAL_STEPS = 6;

    private final PreferenceService preferenceService = new PreferenceService();

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

        // 2. Setup Radius Slider Listeners
        radiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int miles = newVal.intValue();
            radiusValueLabel.setText(miles + " miles");
            updateRadiusHint(miles);
        });

        // 3. Setup Budget Sliders Listeners
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

        // 4. Load from in-memory cache if available, else set defaults
        PreferenceRequest existing = PreferenceService.getCurrentPreferences();
        if (existing != null) {
            populateFromRequest(existing);
        } else {
            resetDefaults();
        }

        // 5. Initialize view to Step 1
        showStep(1);
    }

    private void showStep(int step) {
        this.currentStep = step;

        // Toggle Visibility & Managed state
        setPaneActive(step1Pane, step == 1);
        setPaneActive(step2Pane, step == 2);
        setPaneActive(step3Pane, step == 3);
        setPaneActive(step4Pane, step == 4);
        setPaneActive(step5Pane, step == 5);
        setPaneActive(step6Pane, step == 6);

        // Update Progress & Headings
        double progress = (double) step / TOTAL_STEPS;
        quizProgressBar.setProgress(progress);
        stepCounterLabel.setText("Step " + step + " of " + TOTAL_STEPS);

        switch (step) {
            case 1:
                stepHeadingLabel.setText("Step 1: Daily Schedule & Wake-Up Time");
                break;
            case 2:
                stepHeadingLabel.setText("Step 2: Base Exploration Radius");
                break;
            case 3:
                stepHeadingLabel.setText("Step 3: Travel Experience & Navigation Comfort");
                break;
            case 4:
                stepHeadingLabel.setText("Step 4: Categorical Budget Allocation");
                break;
            case 5:
                stepHeadingLabel.setText("Step 5: Interests & Activity Vibes");
                break;
            case 6:
                stepHeadingLabel.setText("Step 6: Review & Finalize Profile");
                updateSummary();
                break;
        }

        // Navigation Controls State
        prevBtn.setVisible(step > 1);

        if (step == 5) {
            nextBtn.setText("Review Profile →");
            nextBtn.setVisible(true);
        } else if (step == 6) {
            nextBtn.setVisible(false); // Actions handled by Save & Continue on Step 6
        } else {
            nextBtn.setText("Next Question →");
            nextBtn.setVisible(true);
        }

        clearStatus();
    }

    private void setPaneActive(VBox pane, boolean active) {
        if (pane != null) {
            pane.setVisible(active);
            pane.setManaged(active);
        }
    }

    @FXML
    public void handleNextStep(ActionEvent event) {
        if (currentStep == 5) {
            // Validate at least one interest is chosen before advancing to Review
            List<String> interests = getSelectedInterests();
            if (interests.isEmpty()) {
                showStatus("Please select at least one interest before reviewing.", true);
                return;
            }
        }

        if (currentStep < TOTAL_STEPS) {
            showStep(currentStep + 1);
        }
    }

    @FXML
    public void handlePrevStep(ActionEvent event) {
        if (currentStep > 1) {
            showStep(currentStep - 1);
        }
    }

    // Preset Handlers for Step 1
    @FXML
    public void setPresetEarly(ActionEvent event) {
        wakeTimeCombo.setValue("07:00 AM");
    }

    @FXML
    public void setPresetStandard(ActionEvent event) {
        wakeTimeCombo.setValue("08:30 AM");
    }

    @FXML
    public void setPresetLate(ActionEvent event) {
        wakeTimeCombo.setValue("10:00 AM");
    }

    private void updateRadiusHint(int miles) {
        if (radiusHintLabel == null) return;
        if (miles <= 8) {
            radiusHintLabel.setText("🚶 Neighborhood Explorer (Walkable & quick hops)");
        } else if (miles <= 22) {
            radiusHintLabel.setText("🚇 City-Wide & Metro Transit Explorer");
        } else {
            radiusHintLabel.setText("🚆 Regional Excursions & Day Trip Distance");
        }
    }

    private void updateTotalBudget() {
        int transit = (int) transitSlider.getValue();
        int lodging = (int) lodgingSlider.getValue();
        int food = (int) foodSlider.getValue();
        int activities = (int) activitiesSlider.getValue();
        int total = transit + lodging + food + activities;

        if (totalBudgetLabel != null) {
            totalBudgetLabel.setText("$" + total + " Total");
        }
        if (totalBreakdownLabel != null) {
            totalBreakdownLabel.setText("$" + transit + " transit + $" + lodging + " hotel + $" + food + " food + $" + activities + " activities");
        }
    }

    private List<String> getSelectedInterests() {
        List<String> interests = new ArrayList<>();
        if (interestFood.isSelected()) interests.add("food");
        if (interestCulture.isSelected()) interests.add("culture");
        if (interestOutdoors.isSelected()) interests.add("outdoors");
        if (interestNightlife.isSelected()) interests.add("nightlife");
        if (interestShopping.isSelected()) interests.add("shopping");
        if (interestRelaxation.isSelected()) interests.add("relaxation");
        if (interestSports.isSelected()) interests.add("sports");
        if (interestTour.isSelected()) interests.add("tour");
        return interests;
    }

    private void updateSummary() {
        if (summaryWakeTime != null) {
            summaryWakeTime.setText(wakeTimeCombo.getValue() != null ? wakeTimeCombo.getValue() : "08:30 AM");
        }
        if (summaryRadius != null) {
            summaryRadius.setText(((int) radiusSlider.getValue()) + " miles from base hotel");
        }
        if (summaryExperience != null) {
            if (noviceRadio.isSelected()) summaryExperience.setText("Novice Traveler");
            else if (expertRadio.isSelected()) summaryExperience.setText("Expert Adventurer");
            else summaryExperience.setText("Intermediate Explorer");
        }
        if (summaryTotalBudget != null) {
            int total = (int) (transitSlider.getValue() + lodgingSlider.getValue() + foodSlider.getValue() + activitiesSlider.getValue());
            summaryTotalBudget.setText("$" + total + " estimated per person");
        }
        if (summaryPerBlockCap != null) {
            summaryPerBlockCap.setText("$" + ((int) perBlockCapSlider.getValue()) + " / activity");
        }
        if (summaryInterests != null) {
            List<String> list = getSelectedInterests();
            if (list.isEmpty()) {
                summaryInterests.setText("None selected");
            } else {
                List<String> formatted = new ArrayList<>();
                for (String s : list) {
                    formatted.add(s.substring(0, 1).toUpperCase() + s.substring(1));
                }
                summaryInterests.setText(String.join(", ", formatted));
            }
        }
    }

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

        List<String> interests = getSelectedInterests();
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

    @FXML
    public void handleLoadPreferences(ActionEvent event) {
        showStatus("Loading preferences from server...", false);
        PreferenceService.LoadResult result = preferenceService.loadPreferences(DEFAULT_TRIP_ID, DEFAULT_USER_ID);

        if (result.isSuccess() && result.getPreferences() != null) {
            populateFromRequest(result.getPreferences());
            showStatus("✓ " + result.getMessage(), false);
            showStep(6); // Jump straight to Review to see loaded values
        } else {
            showStatus("✗ " + result.getMessage(), true);
        }
    }

    @FXML
    public void handleSaveAndContinue(ActionEvent event) {
        PreferenceRequest request = buildRequestFromInputs();
        if (request == null) return;

        PreferenceService.SaveResult result = preferenceService.savePreferences(request);
        if (result.isSuccess()) {
            showStatus("✓ Preferences saved! Taking you to the dashboard...", false);
            NavigationManager.getInstance().navigateTo("dashboard");
        } else {
            showStatus("✗ " + result.getMessage(), true);
        }
    }

    @FXML
    public void handleReset(ActionEvent event) {
        resetDefaults();
        showStatus("Preferences reset to defaults.", false);
        showStep(1);
    }

    private void resetDefaults() {
        wakeTimeCombo.setValue("08:30 AM");
        intermediateRadio.setSelected(true);
        radiusSlider.setValue(15);
        radiusValueLabel.setText("15 miles");
        updateRadiusHint(15);

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
            updateRadiusHint(req.getMaxRadiusMiles());
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
        updateSummary();
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
