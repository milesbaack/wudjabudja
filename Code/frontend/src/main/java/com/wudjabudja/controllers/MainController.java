package com.wudjabudja.controllers;

import com.wudjabudja.models.Trip;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.services.PreferenceService;
import com.wudjabudja.session.AppSession;
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the Main Application Shell (NAV-01).
 * Manages persistent sidebar navigation, active trip context display,
 * crisp vector nav icons, and smooth view swapping in the center StackPane.
 */
public class MainController implements Initializable {

    @FXML private StackPane brandLogoBadge;
    @FXML private StackPane contentArea;

    @FXML private Label sidebarTripEyebrowLabel;
    @FXML private Label sidebarTripTitleLabel;
    @FXML private Label sidebarTripMetaLabel;

    @FXML private Button navTripSetupBtn;
    @FXML private Button navDashboardBtn;
    @FXML private Button navTripsBtn;
    @FXML private Button navPreferencesBtn;
    @FXML private Button navItineraryBtn;
    @FXML private Button navVotingBtn;
    @FXML private Button navBriefingBtn;

    @FXML private Label sidebarAvatarLabel;
    @FXML private Label sidebarUserNameLabel;
    @FXML private Label sidebarUserEmailLabel;
    @FXML private Button logoutBtn;

    private final Map<Button, String> navIconPaths = new LinkedHashMap<>();
    private List<Button> tripScopedButtons;
    private Button currentActiveButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        NavigationManager.getInstance().setMainController(this);

        if (brandLogoBadge != null) {
            brandLogoBadge.getChildren().setAll(Icons.create(Icons.COMPASS, 20, "#FFFFFF"));
        }

        String userName = AppSession.getLoggedInName();
        String userEmail = AppSession.getLoggedInEmail();
        if (sidebarUserNameLabel != null) {
            sidebarUserNameLabel.setText(userName);
        }
        if (sidebarUserEmailLabel != null) {
            sidebarUserEmailLabel.setText(userEmail);
        }
        if (sidebarAvatarLabel != null) {
            sidebarAvatarLabel.setText(UiTheme.initials(userName != null && !userName.isBlank() ? userName : userEmail));
        }
        if (logoutBtn != null) {
            logoutBtn.setGraphic(Icons.create(Icons.LOGOUT, 15, "#F87171"));
        }

        navIconPaths.put(navTripSetupBtn, Icons.COMPASS);
        navIconPaths.put(navDashboardBtn, Icons.DASHBOARD);
        navIconPaths.put(navTripsBtn, Icons.USERS);
        navIconPaths.put(navPreferencesBtn, Icons.SLIDERS);
        navIconPaths.put(navItineraryBtn, Icons.CALENDAR);
        navIconPaths.put(navVotingBtn, Icons.AWARD);
        navIconPaths.put(navBriefingBtn, Icons.BOOK);

        tripScopedButtons = List.of(
                navTripsBtn,
                navPreferencesBtn,
                navItineraryBtn,
                navVotingBtn,
                navBriefingBtn
        );

        refreshTripContext();

        String initialView = NavigationManager.getInstance().getInitialViewPath();
        if (initialView != null) {
            NavigationManager.getInstance().setInitialViewPath(null);
            if ("/com/wudjabudja/views/PreferenceView.fxml".equals(initialView)) {
                showPreferences(null);
            } else {
                loadCenterView(initialView);
            }
        } else {
            showTripSetup(null);
        }
    }

    /**
     * Synchronizes the sidebar's active trip badge and dims trip-dependent items when no trip is open.
     */
    public void refreshTripContext() {
        Trip trip = AppSession.getCurrentTrip();
        boolean hasTrip = trip != null;

        if (sidebarTripEyebrowLabel != null && sidebarTripTitleLabel != null && sidebarTripMetaLabel != null) {
            if (hasTrip) {
                sidebarTripEyebrowLabel.setText("ACTIVE TRIP");
                sidebarTripTitleLabel.setText(trip.title() != null && !trip.title().isBlank() ? trip.title() : trip.destination());
                sidebarTripMetaLabel.setText(trip.destination() + " · " + trip.dateRangeText());
            } else {
                sidebarTripEyebrowLabel.setText("NO ACTIVE TRIP");
                sidebarTripTitleLabel.setText("Ready to plan?");
                sidebarTripMetaLabel.setText("Create or open a trip to unlock tools");
            }
        }

        if (tripScopedButtons != null) {
            for (Button btn : tripScopedButtons) {
                if (btn == null) continue;
                btn.getStyleClass().remove("nav-btn-locked");
                if (!hasTrip && btn != currentActiveButton) {
                    btn.getStyleClass().add("nav-btn-locked");
                }
            }
        }
    }

    @FXML
    public void showTripSetup(ActionEvent event) {
        setActiveButton(navTripSetupBtn);
        loadCenterView("/com/wudjabudja/views/TripSetupView.fxml");
    }

    @FXML
    public void showDashboard(ActionEvent event) {
        setActiveButton(navDashboardBtn);
        loadCenterView("/com/wudjabudja/views/DashboardView.fxml");
    }

    @FXML
    public void showTrips(ActionEvent event) {
        if (!AppSession.hasTrip()) {
            AppSession.setFlashMessage("Create a trip (or open one under \"Your Trips\") to manage travelers and view shared options.");
            showTripSetup(null);
            return;
        }
        setActiveButton(navTripsBtn);
        loadCenterView("/com/wudjabudja/views/TripsView.fxml");
    }

    @FXML
    public void showPreferences(ActionEvent event) {
        if (!AppSession.hasTrip()) {
            AppSession.setFlashMessage("Create a trip first, then take the preferences quiz for it.");
            showTripSetup(null);
            return;
        }
        setActiveButton(navPreferencesBtn);
        loadCenterView("/com/wudjabudja/views/PreferenceView.fxml");
    }

    @FXML
    public void showItinerary(ActionEvent event) {
        if (!AppSession.hasTrip()) {
            AppSession.setFlashMessage("Create a trip (or open one under \"Your Trips\") to see its itinerary.");
            showTripSetup(null);
            return;
        }
        setActiveButton(navItineraryBtn);
        loadCenterView("/com/wudjabudja/views/ItineraryView.fxml");
    }

    @FXML
    public void showVoting(ActionEvent event) {
        if (!AppSession.hasTrip()) {
            AppSession.setFlashMessage("Create a trip (or open one under \"Your Trips\") before voting on activities.");
            showTripSetup(null);
            return;
        }
        setActiveButton(navVotingBtn);
        loadCenterView("/com/wudjabudja/views/VotingView.fxml");
    }

    @FXML
    public void showBriefing(ActionEvent event) {
        if (!AppSession.hasTrip()) {
            AppSession.setFlashMessage("Create a trip (or open one under \"Your Trips\") before opening the Trip Briefing.");
            showTripSetup(null);
            return;
        }
        setActiveButton(navBriefingBtn);
        loadCenterView("/com/wudjabudja/views/BriefingView.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        AppSession.clear();
        PreferenceService.setCurrentPreferences(null);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        NavigationManager.getInstance().switchScene(stage, "/com/wudjabudja/views/LoginView.fxml");
    }

    /**
     * Swaps the node displayed in the dynamic center StackPane with a smooth fade-in.
     *
     * @param fxmlPath Path to the target FXML resource.
     * @return The loaded root node, or null on error.
     */
    public Node loadCenterView(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                System.err.println("MainController: View resource not found: " + fxmlPath);
                return null;
            }
            Node view = FXMLLoader.load(resource);
            contentArea.getChildren().setAll(view);
            UiTheme.fadeIn(view);
            refreshTripContext();
            return view;
        } catch (IOException e) {
            System.err.println("MainController: Failed to load center view: " + fxmlPath);
            e.printStackTrace();
            return null;
        }
    }

    private void setActiveButton(Button activeButton) {
        this.currentActiveButton = activeButton;
        for (Map.Entry<Button, String> entry : navIconPaths.entrySet()) {
            Button btn = entry.getKey();
            if (btn == null) continue;
            boolean active = (btn == activeButton);
            btn.getStyleClass().removeAll("nav-btn", "nav-btn-active");
            btn.getStyleClass().add(active ? "nav-btn-active" : "nav-btn");
            btn.setGraphic(Icons.create(entry.getValue(), 16, active ? "#2DD4BF" : "#94A3B8"));
        }
        refreshTripContext();
    }
}
