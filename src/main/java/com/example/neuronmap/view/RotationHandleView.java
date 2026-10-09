package com.example.neuronmap.view;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * Відображає ручку, за допомогою якої користувач повертає нейрон.
 */
public final class RotationHandleView extends Button {

    public static final double SIZE = 22.0;
    public static final double GAP = 8.0;

    private final StackPane graphicPane = new StackPane();
    private final Circle background = new Circle(SIZE / 2.0);
    private final Label glyph = new Label("↻");

    /**
     * Повертає результат операції «обертання обробити відображення».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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
 * Виконує операцію «центр».
 *
 * @param parentCenter значення, що визначає центр для цієї операції.
 */
public void placeCenterAt(Point2D parentCenter) {
        if (parentCenter == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
 * Задає або оновлює значення, повʼязані з «графічний обертання».
 *
 * @param degrees кут повороту в градусах.
 */
public void setVisualRotation(double degrees) {
        graphicPane.setRotate(degrees);
    }

/**
 * Завершує або скасовує дію, повʼязану з «потрібні дані».
 */
public void dispose() {
        graphicPane.setRotate(0.0);
    }
}
