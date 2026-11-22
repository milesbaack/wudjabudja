package com.wudjabudja.controllers;

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

public class LoginController implements Initializable {

    @FXML private StackPane brandLogoBadge;
    @FXML private StackPane featureIcon1;
    @FXML private StackPane featureIcon2;
    @FXML private StackPane featureIcon3;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (brandLogoBadge != null) {
            brandLogoBadge.getChildren().setAll(Icons.create(Icons.COMPASS, 20, "#FFFFFF"));
        }
        if (featureIcon1 != null) {
            featureIcon1.getChildren().setAll(Icons.create(Icons.SLIDERS, 18, "#2DD4BF"));
        }
        if (featureIcon2 != null) {
            featureIcon2.getChildren().setAll(Icons.create(Icons.SPARKLES, 18, "#2DD4BF"));
        }
        if (featureIcon3 != null) {
            featureIcon3.getChildren().setAll(Icons.create(Icons.AWARD, 18, "#2DD4BF"));
        }
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both your email address and password.");
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            showError("Please enter a valid email address.");
            return;
        }

        clearError();
        AppSession.setLoggedInUser(null, email);
        System.out.println("Client validation passed! Ready to authenticate: " + email);

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        NavigationManager.getInstance().switchScene(stage, "/com/wudjabudja/views/MainView.fxml");
    }

    @FXML
    public void goToRegister(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        NavigationManager.getInstance().switchScene(stage, "/com/wudjabudja/views/RegisterView.fxml");
    }

    private void showError(String message) {
        UiTheme.showStatus(errorLabel, message, true);
    }

    private void clearError() {
        UiTheme.clearStatus(errorLabel);
    }
}
