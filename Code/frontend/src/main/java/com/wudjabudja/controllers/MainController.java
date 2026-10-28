package com.wudjabudja.controllers;

import com.wudjabudja.navigation.NavigationManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Main Application Shell (NAV-01).
 * Manages persistent sidebar navigation and dynamic view swapping in the center StackPane.
 */
public class MainController implements Initializable {

    @FXML
    private StackPane contentArea;

    @FXML
    private Button navDashboardBtn;

    @FXML
    private Button navTripsBtn;

    @FXML
    private Button navPreferencesBtn;

    @FXML
    private Button navVotingBtn;

    @FXML
    private Button navBriefingBtn;

    private List<Button> navButtons;

    private static final String STYLE_INACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #94a3b8; " +
            "-fx-alignment: BASELINE_LEFT; -fx-padding: 10 16; -fx-font-size: 14px; -fx-cursor: hand;";

    private static final String STYLE_ACTIVE =
            "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-alignment: BASELINE_LEFT; -fx-padding: 10 16; -fx-font-size: 14px; -fx-background-radius: 6;";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        NavigationManager.getInstance().setMainController(this);

        navButtons = Arrays.asList(
                navDashboardBtn,
                navTripsBtn,
                navPreferencesBtn,
                navVotingBtn,
                navBriefingBtn
        );

        // Check if an initial starting view was requested (e.g. Onboarding Quiz after registration)
        String initialView = NavigationManager.getInstance().getInitialViewPath();
        if (initialView != null) {
            NavigationManager.getInstance().setInitialViewPath(null); // Clear after consumption
            if ("/com/wudjabudja/views/PreferenceView.fxml".equals(initialView)) {
                showPreferences(null);
            } else {
                loadCenterView(initialView);
            }
        } else {
            // Load default starting view
            showDashboard(null);
        }
    }

    @FXML
    public void showDashboard(ActionEvent event) {
        setActiveButton(navDashboardBtn);
        loadCenterView("/com/wudjabudja/views/DashboardView.fxml");
    }

    @FXML
    public void showTrips(ActionEvent event) {
        setActiveButton(navTripsBtn);
        loadCenterView("/com/wudjabudja/views/TripsView.fxml");
    }

    @FXML
    public void showPreferences(ActionEvent event) {
        setActiveButton(navPreferencesBtn);
        loadCenterView("/com/wudjabudja/views/PreferenceView.fxml");
    }

    @FXML
    public void showVoting(ActionEvent event) {
        setActiveButton(navVotingBtn);
        loadCenterView("/com/wudjabudja/views/VotingView.fxml");
    }

    @FXML
    public void showBriefing(ActionEvent event) {
        setActiveButton(navBriefingBtn);
        loadCenterView("/com/wudjabudja/views/BriefingView.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/com/wudjabudja/views/LoginView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("WudjaBudja — Login");
            stage.setScene(new Scene(loginRoot));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Swaps the node displayed in the dynamic center StackPane.
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
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            return view;
        } catch (IOException e) {
            System.err.println("MainController: Failed to load center view: " + fxmlPath);
            e.printStackTrace();
            return null;
        }
    }

    private void setActiveButton(Button activeButton) {
        if (navButtons == null) return;
        for (Button btn : navButtons) {
            if (btn == null) continue;
            if (btn == activeButton) {
                btn.setStyle(STYLE_ACTIVE);
            } else {
                btn.setStyle(STYLE_INACTIVE);
            }
        }
    }
}
