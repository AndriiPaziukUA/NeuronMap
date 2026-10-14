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

    /**
     * Створює рядок стану, який може приймати наведення, коли показується повідомлення.
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
        root.setPickOnBounds(true);

        label.getStyleClass().add("status-label");
        label.setMouseTransparent(true);
        label.setVisible(false);

        root.getChildren().add(label);
    }

    /**
     * Повертає кореневий вузол інтерфейсу та область наведення для повідомлення.
     *
     * @return кореневий вузол рядка стану.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Повертає конкретну мітку, у якій відображаються повідомлення рядка стану.
     *
     * @return мітка повідомлення.
     */
    Label messageLabel() {
        return label;
    }

    /**
     * Установлює текст повідомлення та визначає, чи має мітка бути видимою.
     *
     * @param text текст, який потрібно показати; порожній текст приховує мітку.
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
