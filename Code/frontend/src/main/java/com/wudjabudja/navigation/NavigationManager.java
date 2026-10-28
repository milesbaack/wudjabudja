package com.wudjabudja.navigation;

import com.wudjabudja.controllers.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Singleton navigation service to handle view swapping inside the Main App Shell
 * and scene transitions across stages.
 */
public class NavigationManager {

    private static NavigationManager instance;
    private MainController mainController;
    private String initialViewPath;

    private NavigationManager() {}

    public static synchronized NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    public void setInitialViewPath(String path) {
        this.initialViewPath = path;
    }

    public String getInitialViewPath() {
        return initialViewPath;
    }

    /**
     * Swaps the view inside the central StackPane of the main application shell.
     *
     * @param fxmlPath Resource path to the FXML view.
     * @return The loaded root Node, or null if failed.
     */
    public Node loadCenterView(String fxmlPath) {
        if (mainController == null) {
            System.err.println("NavigationManager: MainController is not registered!");
            return null;
        }
        return mainController.loadCenterView(fxmlPath);
    }

    /**
     * Navigates to a specific primary section in the Main Shell,
     * updating both the view and the active sidebar indicator.
     */
    public void navigateTo(String destination) {
        if (mainController == null) {
            System.err.println("NavigationManager: MainController is not registered!");
            return;
        }
        if ("preferences".equalsIgnoreCase(destination) || "quiz".equalsIgnoreCase(destination)) {
            mainController.showPreferences(null);
        } else if ("dashboard".equalsIgnoreCase(destination)) {
            mainController.showDashboard(null);
        } else if ("voting".equalsIgnoreCase(destination)) {
            mainController.showVoting(null);
        } else if ("trips".equalsIgnoreCase(destination)) {
            mainController.showTrips(null);
        } else if ("briefing".equalsIgnoreCase(destination)) {
            mainController.showBriefing(null);
        }
    }

    /**
     * Replaces the entire stage's scene (e.g. during Login -> Main Shell or Logout).
     */
    public void switchScene(Stage stage, String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            System.err.println("NavigationManager: Failed to load scene from " + fxmlPath);
            e.printStackTrace();
        }
    }
}
