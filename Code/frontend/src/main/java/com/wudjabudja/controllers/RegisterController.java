package com.wudjabudja.controllers;

import com.wudjabudja.models.Participant;
import com.wudjabudja.navigation.NavigationManager;
import com.wudjabudja.session.AppSession;
import com.wudjabudja.ui.Icons;
import com.wudjabudja.ui.UiTheme;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    @FXML private StackPane brandLogoBadge;
    @FXML private StackPane featureIcon1;
    @FXML private StackPane featureIcon2;
    @FXML private StackPane featureIcon3;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (brandLogoBadge != null) {
            brandLogoBadge.getChildren().setAll(Icons.create(Icons.COMPASS, 20, "#FFFFFF"));
        }
        if (featureIcon1 != null) {
            featureIcon1.getChildren().setAll(Icons.create(Icons.USERS, 18, "#2DD4BF"));
        }
        if (featureIcon2 != null) {
            featureIcon2.getChildren().setAll(Icons.create(Icons.CALENDAR, 18, "#2DD4BF"));
        }
        if (featureIcon3 != null) {
            featureIcon3.getChildren().setAll(Icons.create(Icons.BOOK, 18, "#2DD4BF"));
        }
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill out all fields.");
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            showError("Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        clearError();
        System.out.println("Registration valid for: " + fullName + " (" + email + ")");

        AppSession.setLoggedInUser(fullName, email);
        AppSession.setActiveTraveler(new Participant(AppSession.USER_ID, email, fullName, "leader", false));
        AppSession.setFlashMessage("Welcome, " + fullName + "! Create your first trip below to start the preferences quiz.", false);

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        NavigationManager.getInstance().switchScene(stage, "/com/wudjabudja/views/MainView.fxml");
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        NavigationManager.getInstance().switchScene(stage, "/com/wudjabudja/views/LoginView.fxml");
    }

    private void showError(String message) {
        UiTheme.showStatus(errorLabel, message, true);
    }

    private void clearError() {
        UiTheme.clearStatus(errorLabel);
    }
}
