package com.wudjabudja.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label errorLabel;

    @FXML
    public void handleRegister(ActionEvent event) {
        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        // 1. Non-empty check for all fields
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill out all fields.");
            return;
        }

        // 2. Email format check
        if (!email.contains("@") || !email.contains(".")) {
            showError("Please enter a valid email address.");
            return;
        }

        // 3. Password length check
        if (password.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        // 4. Passwords match check (Crucial requirement for AUTH-04!)
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        // Passed all client-side validation!
        clearError();
        System.out.println("Registration valid for: " + fullName + " (" + email + ")");
        
        // In Sprint 2 (AUTH-01), this triggers POST /api/auth/register
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        switchScene(event, "/com/wudjabudja/views/LoginView.fxml");
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private void switchScene(ActionEvent event, String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load view: " + fxmlPath);
        }
    }
}
