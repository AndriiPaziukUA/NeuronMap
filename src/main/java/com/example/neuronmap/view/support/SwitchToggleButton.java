package com.example.neuronmap.view.support;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.Objects;

/**
 * Надає перемикач із графічною доріжкою та рухомим круглим індикатором.
 * Вигляд конкретного перемикача задається локальними CSS-класами власника, а не стилями цього класу.
 */
public final class SwitchToggleButton extends ToggleButton {

    private static final double TRACK_WIDTH = 42.0;
    private static final double TRACK_HEIGHT = 24.0;
    private static final double THUMB_RADIUS = 8.0;
    private static final double THUMB_OFFSET = 9.0;

    private final Circle thumb = new Circle(THUMB_RADIUS);
    private final ChangeListener<Boolean> selectionListener =
            (_, _, selected) -> thumb.setTranslateX(selected ? THUMB_OFFSET : -THUMB_OFFSET);

    /**
     * Створює доступний для стилізації перемикач і синхронізує положення індикатора з вибраним станом.
     *
     * @param graphicStyleClass CSS-клас графічної області перемикача.
     * @param trackStyleClass CSS-клас доріжки перемикача.
     * @param thumbStyleClass CSS-клас круглого індикатора.
     * @throws NullPointerException якщо будь-який CSS-клас не задано.
     */
    public SwitchToggleButton(String graphicStyleClass, String trackStyleClass, String thumbStyleClass) {
        Objects.requireNonNull(graphicStyleClass, "graphicStyleClass");
        Objects.requireNonNull(trackStyleClass, "trackStyleClass");
        Objects.requireNonNull(thumbStyleClass, "thumbStyleClass");

        Rectangle track = new Rectangle(TRACK_WIDTH, TRACK_HEIGHT);
        track.getStyleClass().add(trackStyleClass);
        track.setArcWidth(TRACK_HEIGHT);
        track.setArcHeight(TRACK_HEIGHT);
        thumb.getStyleClass().add(thumbStyleClass);
        thumb.setTranslateX(-THUMB_OFFSET);
        selectedProperty().addListener(selectionListener);

        StackPane graphic = new StackPane(track, thumb);
        graphic.getStyleClass().add(graphicStyleClass);
        graphic.setMinSize(TRACK_WIDTH, TRACK_HEIGHT);
        graphic.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        graphic.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);
        graphic.setMouseTransparent(true);
        setGraphic(graphic);
        setText("");
    }

    /** Від'єднує внутрішній слухач стану перед остаточним звільненням подання-власника. */
    public void dispose() {
        selectedProperty().removeListener(selectionListener);
        setOnAction(null);
        setDisable(true);
    }
}
