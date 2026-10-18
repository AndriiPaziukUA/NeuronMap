package com.example.neuronmap.view.support;

import javafx.scene.control.Button;
import javafx.scene.layout.Region;

import java.util.Objects;

/** Узгоджує розміри пари кнопок за природними розмірами їхнього вмісту, не задаючи стилів. */
public final class ContentSizedButtonPair {

    /** Забороняє створення екземплярів допоміжного класу. */
    private ContentSizedButtonPair() {
    }

    /**
     * Встановлює парі кнопок однаковий мінімальний розмір за шириною й висотою більшої кнопки.
     * Стилі залишаються власністю подань, які передають кнопки до цього методу.
     *
     * @param first перша кнопка пари.
     * @param second друга кнопка пари.
     * @throws NullPointerException якщо будь-яку кнопку не задано.
     */
    public static void sizeToLargestContent(Button first, Button second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        resetDimensions(first);
        resetDimensions(second);
        first.applyCss();
        second.applyCss();

        double width = Math.max(first.prefWidth(-1.0), second.prefWidth(-1.0));
        double height = Math.max(first.prefHeight(width), second.prefHeight(width));
        applyDimensions(first, width, height);
        applyDimensions(second, width, height);
    }

    /**
     * Повертає кнопку до розмірів, які JavaFX обчислює з її вмісту та стилів.
     *
     * @param button кнопка, розміри якої потрібно скинути.
     */
    private static void resetDimensions(Button button) {
        button.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }

    /**
     * Установлює спільний мінімальний розмір, не фіксуючи preferred і maximum розміри.
     *
     * @param button кнопка, розміри якої потрібно узгодити.
     * @param width спільна мінімальна ширина.
     * @param height спільна мінімальна висота.
     */
    private static void applyDimensions(Button button, double width, double height) {
        button.setMinSize(width, height);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }
}
