package com.example.neuronmap.view.neuron;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * Відображає інтерактивну ручку обертання та встановлює її положення й візуальний кут.
 */
public final class RotationHandleView extends Button {

    public static final double SIZE = 22.0;
    /** Геометричний зазор між нейроном і ручкою обертання; використовується позиціонером накладок. */
    public static final double GAP = 8.0;

    private final StackPane graphicPane = new StackPane();
    private final Circle background = new Circle(SIZE / 2.0);
    private final Label glyph = new Label("↻");

    public RotationHandleView() {
        getStyleClass().add("rotation-handle-button");

        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setManaged(false);
        resize(SIZE, SIZE);

        setPadding(Insets.EMPTY);
        setAlignment(Pos.CENTER);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setFocusTraversable(false);
        setPickOnBounds(true);
        setMouseTransparent(false);

        background.getStyleClass().add("rotation-handle");
        background.setMouseTransparent(true);

        glyph.getStyleClass().add("rotation-handle-glyph");
        glyph.setMouseTransparent(true);

        graphicPane.setMinSize(SIZE, SIZE);
        graphicPane.setPrefSize(SIZE, SIZE);
        graphicPane.setMaxSize(SIZE, SIZE);
        graphicPane.setAlignment(Pos.CENTER);
        graphicPane.setMouseTransparent(true);
        graphicPane.getChildren().addAll(
                background,
                glyph
        );

        setGraphic(graphicPane);
    }

    /**
     * Переміщує ручку обертання так, щоб її центр збігався з переданою точкою батьківської області.
     *
     * @param parentCenter центр ручки в локальних координатах батьківського контейнера.
     * @throws IllegalArgumentException якщо координату центру не задано.
     */
    public void placeCenterAt(Point2D parentCenter) {
        if (parentCenter == null) {

            throw new IllegalArgumentException(
                    "parentCenter must not be null"
            );
        }

        setTranslateX(0.0);
        setTranslateY(0.0);
        setScaleX(1.0);
        setScaleY(1.0);
        setRotate(0.0);
        resize(SIZE, SIZE);

        relocate(
                parentCenter.getX() - SIZE / 2.0,
                parentCenter.getY() - SIZE / 2.0
        );
    }

    /**
     * Установлює візуальний кут графічного символу ручки.
     *
     * @param degrees кут повороту в градусах.
     */
    public void setVisualRotation(double degrees) {
        graphicPane.setRotate(degrees);
    }

    /**
     * Скидає візуальний кут внутрішнього графічного елемента перед повторним використанням.
     */
    public void dispose() {
        graphicPane.setRotate(0.0);
    }
}
