package com.wudjabudja.controllers;

import com.wudjabudja.models.Participant;
import com.wudjabudja.models.PreferenceRequest;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.ApiResult;
import com.wudjabudja.services.ParticipantService;
import com.wudjabudja.services.PreferenceService;
import com.wudjabudja.session.AppSession;
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
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
 * Guides the user through a 6-step wizard with interactive clickable option cards,
 * dynamic preset highlighting, and a clickable step indicator strip.
 */
public class PreferenceController implements Initializable {

    // Step Tracking & Header Controls
    @FXML private Label stepCounterLabel;
    @FXML private ProgressBar quizProgressBar;
    @FXML private Label stepHeadingLabel;
    @FXML private Label tripLabel;
    @FXML private ComboBox<Participant> travelerCombo;

    @FXML private Button stepperBtn1;
    @FXML private Button stepperBtn2;
    @FXML private Button stepperBtn3;
    @FXML private Button stepperBtn4;
    @FXML private Button stepperBtn5;
    @FXML private Button stepperBtn6;

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
    @FXML private Button presetEarlyBtn;
    @FXML private Button presetStandardBtn;
    @FXML private Button presetLateBtn;
    @FXML private ComboBox<String> wakeTimeCombo;

    // Step 2: Radius Controls
    @FXML private Slider radiusSlider;
    @FXML private Label radiusValueLabel;
    @FXML private Label radiusHintLabel;

    // Step 3: Experience Controls & Clickable Cards
    @FXML private ToggleGroup experienceGroup;
    @FXML private VBox noviceCard;
    @FXML private VBox intermediateCard;
    @FXML private VBox expertCard;
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

    // Step 5: Interests Checkboxes & Clickable Cards
    @FXML private VBox foodCard;
    @FXML private VBox cultureCard;
    @FXML private VBox outdoorsCard;
    @FXML private VBox nightlifeCard;
    @FXML private VBox shoppingCard;
    @FXML private VBox relaxationCard;
    @FXML private VBox sportsCard;
    @FXML private VBox tourCard;
    @FXML private CheckBox interestFood;
    @FXML private CheckBox interestCulture;
    @FXML private CheckBox interestOutdoors;
    @FXML private CheckBox interestNightlife;
    @FXML private CheckBox interestShopping;
    @FXML private CheckBox interestRelaxation;
    @FXML private CheckBox interestSports;
    @FXML private CheckBox interestTour;

    // Step 6: Review Summary Labels & Action Buttons
    @FXML private Label summaryWakeTime;
    @FXML private Label summaryRadius;
    @FXML private Label summaryExperience;
    @FXML private Label summaryTotalBudget;
    @FXML private Label summaryPerBlockCap;
    @FXML private Label summaryInterests;
    @FXML private Button saveAndContinueBtn;

    // Status Label
    @FXML private Label statusLabel;

    private int currentStep = 1;
    private static final int TOTAL_STEPS = 6;

    private final PreferenceService preferenceService = new PreferenceService();
    private final ParticipantService participantService = new ParticipantService();
    private boolean updatingTravelerCombo = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (prevBtn != null) prevBtn.setGraphic(Icons.create(Icons.ARROW_LEFT, 14, "#334155"));
        if (nextBtn != null) nextBtn.setGraphic(Icons.create(Icons.ARROW_RIGHT, 14, "#FFFFFF"));
        if (saveAndContinueBtn != null) saveAndContinueBtn.setGraphic(Icons.create(Icons.CHECK, 15, "#FFFFFF"));

        // 1. Populate Wake-Up Times
        List<String> times = Arrays.asList(
                "06:00 AM", "06:30 AM", "07:00 AM", "07:30 AM",
                "08:00 AM", "08:30 AM", "09:00 AM", "09:30 AM",
                "10:00 AM", "10:30 AM", "11:00 AM"
        );
        wakeTimeCombo.setItems(FXCollections.observableArrayList(times));
        wakeTimeCombo.setValue("08:30 AM");
        wakeTimeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePresetHighlights(newVal));

        // 2. Setup Radius Slider Listeners
        radiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int miles = newVal.intValue();
            radiusValueLabel.setText(miles + " miles");
            updateRadiusHint(miles);
        });

        // 3. Setup Interactive Experience Cards (Step 3)
        bindExperienceCard(noviceCard, noviceRadio);
        bindExperienceCard(intermediateCard, intermediateRadio);
        bindExperienceCard(expertCard, expertRadio);
        experienceGroup.selectedToggleProperty().addListener((obs, o, n) -> syncExperienceCardStyles());

        // 4. Setup Budget Sliders Listeners (Step 4)
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

        // 5. Setup Interactive Interest Cards (Step 5)
        bindInterestCard(foodCard, interestFood);
        bindInterestCard(cultureCard, interestCulture);
        bindInterestCard(outdoorsCard, interestOutdoors);
        bindInterestCard(nightlifeCard, interestNightlife);
        bindInterestCard(shoppingCard, interestShopping);
        bindInterestCard(relaxationCard, interestRelaxation);
        bindInterestCard(sportsCard, interestSports);
        bindInterestCard(tourCard, interestTour);

        // 6. Banner + Existing/Default State
        updateTripBanner();

        PreferenceRequest existing = PreferenceService.getCurrentPreferences();
        if (existing != null
                && existing.getTripId() != null && existing.getTripId().equals(AppSession.getTripId())
                && existing.getUserId() != null && existing.getUserId().equals(AppSession.getActiveUserId())) {
            populateFromRequest(existing);
        } else {
            resetDefaults();
        }

        showStep(1, false);
        loadTravelersAndActivePreferences();
    }

    private void bindExperienceCard(VBox card, RadioButton radio) {
        if (card == null || radio == null) return;
        card.setOnMouseClicked(e -> {
            if (!radio.isSelected()) {
                radio.setSelected(true);
            }
        });
    }

    private void syncExperienceCardStyles() {
        applyChoiceCardState(noviceCard, noviceRadio != null && noviceRadio.isSelected());
        applyChoiceCardState(intermediateCard, intermediateRadio != null && intermediateRadio.isSelected());
        applyChoiceCardState(expertCard, expertRadio != null && expertRadio.isSelected());
    }

    private void bindInterestCard(VBox card, CheckBox checkBox) {
        if (card == null || checkBox == null) return;
        checkBox.selectedProperty().addListener((obs, o, selected) -> applyChoiceCardState(card, selected));
        card.setOnMouseClicked(e -> {
            if (e.getTarget() != checkBox && !isDescendantOf(e.getTarget(), checkBox)) {
                checkBox.setSelected(!checkBox.isSelected());
            }
        });
        applyChoiceCardState(card, checkBox.isSelected());
    }

    private static boolean isDescendantOf(Object target, Control control) {
        if (!(target instanceof javafx.scene.Node node)) return false;
        javafx.scene.Node curr = node;
        while (curr != null) {
            if (curr == control) return true;
            curr = curr.getParent();
        }
        return false;
    }

    private static void applyChoiceCardState(VBox card, boolean selected) {
        if (card == null) return;
        card.getStyleClass().removeAll("choice-card", "choice-card-selected");
        card.getStyleClass().add(selected ? "choice-card-selected" : "choice-card");
    }

    private void updatePresetHighlights(String wakeTime) {
        setPresetActive(presetEarlyBtn, "07:00 AM".equals(wakeTime));
        setPresetActive(presetStandardBtn, "08:30 AM".equals(wakeTime));
        setPresetActive(presetLateBtn, "10:00 AM".equals(wakeTime));
    }

    private static void setPresetActive(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("preset-btn", "preset-btn-active");
        btn.getStyleClass().add(active ? "preset-btn-active" : "preset-btn");
    }

    private void updateTripBanner() {
        if (tripLabel != null && AppSession.getCurrentTrip() != null) {
            Participant active = AppSession.getActiveTraveler();
            tripLabel.setText(AppSession.getCurrentTrip().title()
                    + "  ·  " + active.shortName());
        }
    }

    private void loadTravelersAndActivePreferences() {
        Long tripId = AppSession.getTripId();
        if (tripId == null) return;
        Long activeUserId = AppSession.getActiveUserId();

        if (travelerCombo != null) {
            updatingTravelerCombo = true;
            travelerCombo.getItems().setAll(AppSession.getActiveTraveler());
            travelerCombo.setValue(AppSession.getActiveTraveler());
            updatingTravelerCombo = false;
        }

        Task<ApiResult<List<Participant>>> task = new Task<>() {
            @Override
            protected ApiResult<List<Participant>> call() {
                return participantService.listParticipants(tripId);
            }
        };
        task.setOnSucceeded(e -> {
            ApiResult<List<Participant>> res = task.getValue();
            if (travelerCombo != null && res.success() && res.data() != null && !res.data().isEmpty()) {
                updatingTravelerCombo = true;
                travelerCombo.getItems().setAll(res.data());
                Participant match = res.data().stream()
                        .filter(p -> p.userId() != null && p.userId().equals(AppSession.getActiveUserId()))
                        .findFirst()
                        .orElse(res.data().get(0));
                AppSession.setActiveTraveler(match);
                travelerCombo.setValue(match);
                updatingTravelerCombo = false;
                updateTripBanner();
            }
            loadPreferencesForActiveTravelerSilently(tripId, AppSession.getActiveUserId());
        });
        task.setOnFailed(e -> loadPreferencesForActiveTravelerSilently(tripId, activeUserId));
        Thread t = new Thread(task, "quiz-participants-load");
        t.setDaemon(true);
        t.start();
    }

    private void loadPreferencesForActiveTravelerSilently(Long tripId, Long userId) {
        Task<PreferenceService.LoadResult> task = new Task<>() {
            @Override
            protected PreferenceService.LoadResult call() {
                return preferenceService.loadPreferences(tripId, userId);
            }
        };
        task.setOnSucceeded(e -> {
            PreferenceService.LoadResult result = task.getValue();
            if (result != null && result.isSuccess() && result.getPreferences() != null) {
                populateFromRequest(result.getPreferences());
                showStatus("Loaded saved quiz answers for " + AppSession.getActiveTraveler().shortName() + ".", false);
            }
        });
        Thread t = new Thread(task, "quiz-pref-preload");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleTravelerSelected(ActionEvent event) {
        if (updatingTravelerCombo || travelerCombo == null) return;
        Participant selected = travelerCombo.getValue();
        if (selected == null || selected.userId() == null) return;

        AppSession.setActiveTraveler(selected);
        PreferenceService.setCurrentPreferences(null);
        updateTripBanner();
        resetDefaults();
        showStep(1);
        showStatus("Switched to " + selected.shortName() + ". Checking for saved quiz answers...", false);
        loadPreferencesForActiveTravelerSilently(AppSession.getTripId(), selected.userId());
    }

    @FXML
    public void goToSharedTrip(ActionEvent event) {
        NavigationManager.getInstance().navigateTo("trips");
    }

    // Stepper Jump Handlers
    @FXML public void jumpToStep1(ActionEvent e) { showStep(1); }
    @FXML public void jumpToStep2(ActionEvent e) { showStep(2); }
    @FXML public void jumpToStep3(ActionEvent e) { showStep(3); }
    @FXML public void jumpToStep4(ActionEvent e) { showStep(4); }
    @FXML public void jumpToStep5(ActionEvent e) { showStep(5); }
    @FXML public void jumpToStep6(ActionEvent e) {
        if (getSelectedInterests().isEmpty()) {
            showStep(5);
            showStatus("Please select at least one interest before reviewing.", true);
            return;
        }
        showStep(6);
    }

    private void showStep(int step) {
        showStep(step, true);
    }

    private void showStep(int step, boolean animate) {
        this.currentStep = step;

        setPaneActive(step1Pane, step == 1, animate);
        setPaneActive(step2Pane, step == 2, animate);
        setPaneActive(step3Pane, step == 3, animate);
        setPaneActive(step4Pane, step == 4, animate);
        setPaneActive(step5Pane, step == 5, animate);
        setPaneActive(step6Pane, step == 6, animate);

        updateStepperPills(step);

        double progress = (double) step / TOTAL_STEPS;
        quizProgressBar.setProgress(progress);
        stepCounterLabel.setText("Step " + step + " of " + TOTAL_STEPS);

        switch (step) {
            case 1 -> stepHeadingLabel.setText("Daily Schedule & Wake-Up Time");
            case 2 -> stepHeadingLabel.setText("Base Exploration Radius");
            case 3 -> stepHeadingLabel.setText("Travel Experience & Navigation Comfort");
            case 4 -> stepHeadingLabel.setText("Categorical Budget Allocation");
            case 5 -> stepHeadingLabel.setText("Interests & Activity Vibes");
            case 6 -> {
                stepHeadingLabel.setText("Review & Finalize Profile");
                updateSummary();
            }
        }

        prevBtn.setVisible(step > 1);
        prevBtn.setManaged(step > 1);

        if (step == 5) {
            nextBtn.setText("Review Profile");
            nextBtn.setVisible(true);
            nextBtn.setManaged(true);
        } else if (step == 6) {
            nextBtn.setVisible(false);
            nextBtn.setManaged(false);
        } else {
            nextBtn.setText("Next Question");
            nextBtn.setVisible(true);
            nextBtn.setManaged(true);
        }

        clearStatus();
    }

    private void updateStepperPills(int activeStep) {
        List<Button> pills = List.of(stepperBtn1, stepperBtn2, stepperBtn3, stepperBtn4, stepperBtn5, stepperBtn6);
        for (int i = 0; i < pills.size(); i++) {
            Button pill = pills.get(i);
            if (pill == null) continue;
            int num = i + 1;
            pill.getStyleClass().removeAll("stepper-pill", "stepper-pill-active", "stepper-pill-done");
            if (num == activeStep) {
                pill.getStyleClass().add("stepper-pill-active");
            } else if (num < activeStep) {
                pill.getStyleClass().add("stepper-pill-done");
            } else {
                pill.getStyleClass().add("stepper-pill");
            }
        }
    }

    private void setPaneActive(VBox pane, boolean active, boolean animate) {
        if (pane != null) {
            pane.setVisible(active);
            pane.setManaged(active);
            if (active) {
                if (animate) {
                    UiTheme.fadeIn(pane);
                } else {
                    pane.setOpacity(1.0);
                }
            }
        }
    }

    @FXML
    public void handleNextStep(ActionEvent event) {
        if (currentStep == 5) {
            if (getSelectedInterests().isEmpty()) {
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

    @FXML public void setPresetEarly(ActionEvent event) { wakeTimeCombo.setValue("07:00 AM"); }
    @FXML public void setPresetStandard(ActionEvent event) { wakeTimeCombo.setValue("08:30 AM"); }
    @FXML public void setPresetLate(ActionEvent event) { wakeTimeCombo.setValue("10:00 AM"); }

    private void updateRadiusHint(int miles) {
        if (radiusHintLabel == null) return;
        if (miles <= 8) {
            radiusHintLabel.setText("Neighborhood Explorer (Walkable & quick hops)");
        } else if (miles <= 22) {
            radiusHintLabel.setText("City-Wide & Metro Transit Explorer");
        } else {
            radiusHintLabel.setText("Regional Excursions & Day Trip Distance");
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
            totalBreakdownLabel.setText("$" + transit + " transit · $" + lodging + " hotel · $" + food + " food · $" + activities + " activities");
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
        if (AppSession.getTripId() == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create one first.", true);
            return null;
        }

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
                AppSession.getTripId(),
                AppSession.getActiveUserId(),
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
            showStatus("Saved preferences for " + AppSession.getActiveTraveler().shortName() + ".", false);
        } else {
            showStatus(result.getMessage(), true);
        }
    }

    @FXML
    public void handleLoadPreferences(ActionEvent event) {
        showStatus("Loading preferences from server...", false);
        if (AppSession.getTripId() == null) {
            showStatus("No trip selected. Use \"Plan a Trip\" to create one first.", true);
            return;
        }
        PreferenceService.LoadResult result = preferenceService.loadPreferences(AppSession.getTripId(), AppSession.getActiveUserId());

        if (result.isSuccess() && result.getPreferences() != null) {
            populateFromRequest(result.getPreferences());
            showStep(6);
            showStatus("Loaded preferences for " + AppSession.getActiveTraveler().shortName() + ".", false);
        } else {
            showStatus(result.getMessage(), true);
        }
    }

    @FXML
    public void handleSaveAndContinue(ActionEvent event) {
        PreferenceRequest request = buildRequestFromInputs();
        if (request == null) return;

        PreferenceService.SaveResult result = preferenceService.savePreferences(request);
        if (result.isSuccess()) {
            AppSession.setFlashMessage("Saved quiz answers for " + AppSession.getActiveTraveler().shortName() + ".", false);
            NavigationManager.getInstance().navigateTo("trips");
        } else {
            showStatus(result.getMessage(), true);
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
        updatePresetHighlights("08:30 AM");
        intermediateRadio.setSelected(true);
        syncExperienceCardStyles();

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
            String display = convertToDisplayTime(req.getWakeTime());
            wakeTimeCombo.setValue(display);
            updatePresetHighlights(display);
        }
        if ("novice".equalsIgnoreCase(req.getExperienceLevel())) noviceRadio.setSelected(true);
        else if ("expert".equalsIgnoreCase(req.getExperienceLevel())) expertRadio.setSelected(true);
        else intermediateRadio.setSelected(true);
        syncExperienceCardStyles();

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
        UiTheme.showStatus(statusLabel, message, isError);
    }

    private void clearStatus() {
        UiTheme.clearStatus(statusLabel);
    }
}
