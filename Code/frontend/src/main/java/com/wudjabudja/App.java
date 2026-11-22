package com.wudjabudja;

import com.wudjabudja.ui.UiTheme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/wudjabudja/views/LoginView.fxml"));
        UiTheme.showAuthScene(primaryStage, root, "WudjaBudja — Sign In");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
