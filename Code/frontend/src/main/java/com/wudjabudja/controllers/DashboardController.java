package com.wudjabudja.controllers;

import com.wudjabudja.navigation.NavigationManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

/**
 * Controller for the Main Dashboard view.
 * Handles dashboard quick-action buttons to navigate across the app.
 */
public class DashboardController {

    @FXML
    public void goToPreferences(ActionEvent event) {
        NavigationManager.getInstance().loadCenterView("/com/wudjabudja/views/PreferenceView.fxml");
    }

    @FXML
    public void goToTrips(ActionEvent event) {
        NavigationManager.getInstance().loadCenterView("/com/wudjabudja/views/TripsView.fxml");
    }

    @FXML
    public void goToVoting(ActionEvent event) {
        NavigationManager.getInstance().loadCenterView("/com/wudjabudja/views/VotingView.fxml");
    }

    @FXML
    public void goToBriefing(ActionEvent event) {
        NavigationManager.getInstance().loadCenterView("/com/wudjabudja/views/BriefingView.fxml");
    }
}
