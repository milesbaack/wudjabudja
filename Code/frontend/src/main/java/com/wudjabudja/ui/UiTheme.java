package com.wudjabudja.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central UI theme helper for WudjaBudja's "Compass" design language.
 * Applies the master stylesheet, standardizes window dimensions, and builds
 * consistent badges, chips, avatars, and status feedback banners.
 */
public final class UiTheme {

    public static final String STYLESHEET_PATH = "/com/wudjabudja/styles/theme.css";

    public static final double AUTH_WIDTH = 960.0;
    public static final double AUTH_HEIGHT = 640.0;
    public static final double AUTH_MIN_WIDTH = 820.0;
    public static final double AUTH_MIN_HEIGHT = 580.0;

    public static final double MAIN_WIDTH = 1240.0;
    public static final double MAIN_HEIGHT = 800.0;
    public static final double MAIN_MIN_WIDTH = 980.0;
    public static final double MAIN_MIN_HEIGHT = 660.0;

    private static final List<String> BADGE_VARIANTS = List.of(
            "badge-teal", "badge-hero-teal", "badge-hero-glass", "badge-dark",
            "badge-success", "badge-amber", "badge-warning", "badge-danger",
            "badge-violet", "badge-sky", "badge-neutral"
    );

    private static final Map<String, String[]> CATEGORY_COLORS = Map.of(
            "food", new String[]{"#FEF3C7", "#92400E"},
            "outdoors", new String[]{"#DCFCE7", "#166534"},
            "culture", new String[]{"#EDE9FE", "#5B21B6"},
            "nightlife", new String[]{"#E0E7FF", "#3730A3"},
            "sports", new String[]{"#FEE2E2", "#991B1B"},
            "shopping", new String[]{"#FCE7F3", "#9D174D"},
            "relaxation", new String[]{"#E0F2FE", "#075985"},
            "tour", new String[]{"#F1F5F9", "#334155"}
    );

    private static final String RESOLVED_FONT_FAMILY = resolvePrimaryFontFamily();

    private UiTheme() {}

    private static String resolvePrimaryFontFamily() {
        List<String> installed = Font.getFamilies();
        for (String candidate : List.of("Avenir", "Helvetica", "Segoe UI", "Arial", "SansSerif")) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return "SansSerif";
    }

    /**
     * Attaches the master Compass stylesheet to the given Scene if not already attached,
     * and guarantees a clean installed font family across macOS, Windows, and Linux.
     */
    public static void apply(Scene scene) {
        if (scene == null) return;
        URL css = UiTheme.class.getResource(STYLESHEET_PATH);
        if (css != null) {
            String url = css.toExternalForm();
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
        if (scene.getRoot() != null && !"Avenir".equals(RESOLVED_FONT_FAMILY)) {
            scene.getRoot().setStyle("-fx-font-family: \"" + RESOLVED_FONT_FAMILY + "\";");
        }
    }

    /**
     * Configures the Stage for an authentication view (Login / Register).
     */
    public static void showAuthScene(Stage stage, Parent root, String title) {
        Scene scene = new Scene(root, AUTH_WIDTH, AUTH_HEIGHT);
        apply(scene);
        stage.setTitle(title);
        stage.setMinWidth(AUTH_MIN_WIDTH);
        stage.setMinHeight(AUTH_MIN_HEIGHT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    /**
     * Configures the Stage for the primary Main Application Shell.
     */
    public static void showMainScene(Stage stage, Parent root, String title) {
        double width = Math.max(MAIN_WIDTH, stage.getWidth() > MAIN_WIDTH ? stage.getWidth() : MAIN_WIDTH);
        double height = Math.max(MAIN_HEIGHT, stage.getHeight() > MAIN_HEIGHT ? stage.getHeight() : MAIN_HEIGHT);
        Scene scene = new Scene(root, width, height);
        apply(scene);
        stage.setTitle(title);
        stage.setMinWidth(MAIN_MIN_WIDTH);
        stage.setMinHeight(MAIN_MIN_HEIGHT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    /**
     * Smooth, subtle 160ms fade-in when swapping center views.
     */
    public static void fadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0.0);
        FadeTransition ft = new FadeTransition(Duration.millis(160), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
    }

    /**
     * Displays a styled inline feedback banner (emerald for success, rose for error).
     */
    public static void showStatus(Label label, String message, boolean isError) {
        if (label == null) return;
        label.setText(message == null ? "" : message);
        label.getStyleClass().removeAll("status-success", "status-error");
        label.getStyleClass().add(isError ? "status-error" : "status-success");
        label.setVisible(true);
        label.setManaged(true);
    }

    /**
     * Hides and clears an inline feedback banner.
     */
    public static void clearStatus(Label label) {
        if (label == null) return;
        label.setText("");
        label.getStyleClass().removeAll("status-success", "status-error");
        label.setVisible(false);
        label.setManaged(false);
    }

    /**
     * Updates a pill badge's semantic variant class cleanly.
     */
    public static void setBadgeVariant(Label badge, String variantClass) {
        if (badge == null) return;
        if (!badge.getStyleClass().contains("badge")) {
            badge.getStyleClass().add("badge");
        }
        badge.getStyleClass().removeAll(BADGE_VARIANTS);
        badge.getStyleClass().add(variantClass);
    }

    /**
     * Creates a consistently styled activity category pill chip.
     */
    public static Label categoryChip(String category) {
        String raw = (category == null || category.isBlank()) ? "other" : category.trim().toLowerCase(Locale.ROOT);
        String[] colors = CATEGORY_COLORS.getOrDefault(raw, new String[]{"#F1F5F9", "#334155"});
        String display = raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
        Label chip = new Label(display);
        chip.getStyleClass().add("chip");
        chip.setStyle("-fx-background-color: " + colors[0] + "; -fx-text-fill: " + colors[1] + ";");
        return chip;
    }

    /**
     * Creates a consistently styled activity cost pill chip.
     */
    public static Label costChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("chip", "badge-success");
        return chip;
    }

    /**
     * Creates a circular initials avatar badge for travelers.
     */
    public static StackPane travelerAvatar(String nameOrEmail) {
        Label text = new Label(initials(nameOrEmail));
        text.getStyleClass().add("traveler-avatar-text");
        StackPane circle = new StackPane(text);
        circle.getStyleClass().add("traveler-avatar");
        return circle;
    }

    /**
     * Computes 1-2 uppercase initials from a full name or email address.
     */
    public static String initials(String nameOrEmail) {
        if (nameOrEmail == null || nameOrEmail.isBlank()) return "WB";
        String clean = nameOrEmail.trim();
        int at = clean.indexOf('@');
        if (at > 0 && !clean.contains(" ")) {
            clean = clean.substring(0, at).replace('.', ' ').replace('_', ' ').replace('-', ' ');
        }
        String[] parts = clean.trim().split("\\s+");
        if (parts.length == 1) {
            String p = parts[0];
            return p.substring(0, Math.min(2, p.length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }
}
