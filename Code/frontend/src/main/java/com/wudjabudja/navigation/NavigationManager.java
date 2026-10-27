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
