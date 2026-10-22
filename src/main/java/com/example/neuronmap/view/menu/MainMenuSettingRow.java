package com.example.neuronmap.view.menu;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Objects;

/** Централізує компонування рядків налаштувань із підписом ліворуч і елементом керування праворуч. */
final class MainMenuSettingRow {

    /** Забороняє створення екземплярів допоміжного класу компонування. */
    private MainMenuSettingRow() {
    }

    /**
     * Застосовує єдине компонування до рядка з підписом і відповідним елементом керування.
     *
     * @param row контейнер рядка налаштування.
     * @param label локалізований опис налаштування.
     * @param control елемент керування, вирівнюваний за правим краєм.
     * @throws NullPointerException якщо контейнер, підпис або елемент керування не задано.
     */
    static void configure(HBox row, Label label, Node control) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(control, "control");
        row.getStyleClass().add("main-menu-setting-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setSpacing(12.0);
        row.setMaxWidth(Double.MAX_VALUE);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox.setHgrow(control, Priority.NEVER);
        row.getChildren().setAll(label, control);
    }
}
