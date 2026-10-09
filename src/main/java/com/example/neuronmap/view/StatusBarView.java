package com.example.neuronmap.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Відображає поточний стан роботи застосунку в рядку стану.
 */
public final class StatusBarView {

    public static final double HEIGHT = 34.0;

    private final StackPane root = new StackPane();
    private final Label label = new Label();

    /**
     * Повертає результат операції «стан відображення».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StatusBarView() {
        root.getStyleClass().add("status-bar");
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(
                new Insets(0, 10, 0, 10)
        );
        root.setMinHeight(HEIGHT);
        root.setPrefHeight(HEIGHT);
        root.setMaxHeight(HEIGHT);
        root.setMouseTransparent(true);
        root.setPickOnBounds(false);

        label.getStyleClass().add("status-label");
        label.setMouseTransparent(true);
        label.setVisible(false);

        root.getChildren().add(label);
    }

    /**
     * Повертає результат операції «вузол».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Задає або оновлює значення, повʼязані з «текст».
     *
     * @param text текст, який потрібно показати або обробити.
     */
    public void setText(String text) {
        boolean visible =
                text != null && !text.isBlank();

        label.setText(
                visible ? text : ""
        );
        label.setVisible(visible);
    }
}
