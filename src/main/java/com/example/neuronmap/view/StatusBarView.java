package com.example.neuronmap.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Надає вузол рядка стану й оновлює текст повідомлення для користувача.
 */
public final class StatusBarView {

    public static final double HEIGHT = 34.0;

    private final StackPane root = new StackPane();
    private final Label label = new Label();

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
     * Повертає кореневий вузол інтерфейсу.
     *
     * @return кореневий вузол інтерфейсу.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Установлює text для поточного об’єкта.
     *
     * @param text текст, який потрібно показати або розібрати.
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
