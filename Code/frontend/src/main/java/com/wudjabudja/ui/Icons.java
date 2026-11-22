package com.wudjabudja.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Lightweight Lucide-inspired vector icon factory using JavaFX SVGPath (24x24 viewBox).
 * Keeps every icon razor-sharp on Retina/HiDPI displays without external image dependencies.
 */
public final class Icons {

    private Icons() {}

    // 24x24 Lucide-style stroke paths
    public static final String COMPASS =
            "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z "
            + "M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36 6.36-2.12z";

    public static final String DASHBOARD =
            "M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z";

    public static final String USERS =
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 "
            + "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z "
            + "M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75";

    public static final String SLIDERS =
            "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 "
            + "M1 14h6 M9 8h6 M17 16h6";

    public static final String CALENDAR =
            "M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z "
            + "M16 2v4 M8 2v4 M3 10h18";

    public static final String AWARD =
            "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12z "
            + "M8.21 13.89L7 23l5-3 5 3-1.21-9.12";

    public static final String BOOK =
            "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z "
            + "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z";

    public static final String LOGOUT =
            "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9";

    public static final String SPARKLES =
            "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z "
            + "M5 3v4 M3 5h4 M19 17v4 M17 19h4";

    public static final String MAP_PIN =
            "M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z";

    public static final String CLOCK =
            "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z "
            + "M12 6v6l4 2";

    public static final String REFRESH =
            "M21 12a9 9 0 1 1-2.64-6.36L21 8 M21 3v5h-5";

    public static final String PLUS =
            "M12 5v14 M5 12h14";

    public static final String CHECK =
            "M20 6L9 17l-5-5";

    public static final String ARROW_RIGHT =
            "M5 12h14 M12 5l7 7-7 7";

    public static final String ARROW_LEFT =
            "M19 12H5 M12 19l-7-7 7-7";

    /**
     * Creates a crisp stroke-based icon scaled cleanly into a square bounding box.
     *
     * @param svgPathData Path data on a 24x24 coordinate grid.
     * @param size        Target rendered size in pixels (e.g. 16.0).
     * @param hexColor    Stroke color hex (e.g. "#94A3B8" or "#FFFFFF").
     */
    public static Node create(String svgPathData, double size, String hexColor) {
        SVGPath path = new SVGPath();
        path.setContent(svgPathData);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web(hexColor));
        path.setStrokeWidth(2.0);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);

        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);

        StackPane wrapper = new StackPane(path);
        wrapper.setMinSize(size, size);
        wrapper.setPrefSize(size, size);
        wrapper.setMaxSize(size, size);
        wrapper.setPickOnBounds(false);
        return wrapper;
    }
}
