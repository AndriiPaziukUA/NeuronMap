package com.example.neuronmap.view;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Знаходить графічний елемент, у якому потрібно показати повідомлення стану.
 */
public final class StatusMessageTargetLocator {

    /**
     * Повертає результат операції «кінцевий».
     *
     * @param label значення, що визначає підпис для цієї операції.
     *
     * @param hoverRegion значення, що визначає наведення для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент Target у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    public record Target(Label label, Node hoverRegion) {
    }

    /**
     * Повертає результат операції «стан повідомлення кінцевий».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private StatusMessageTargetLocator() {
    }

    /**
     * Повертає або знаходить дані, повʼязані з «за текст».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     *
     * @param text текст, який потрібно показати або обробити.
     *
     * @return знайдене значення або порожній Optional, якщо результату немає.
     */
    public static Optional<Target> findByText(
            Scene scene,
            String text
    ) {
        if (scene == null || scene.getRoot() == null) {
            return Optional.empty();
        }

        List<Label> labels = new ArrayList<>();
        collectLabels(scene.getRoot(), labels::add);

        Label label = labels.stream()
                .filter(candidate -> text != null
                        && text.equals(candidate.getText()))
                .max(Comparator.comparingDouble(
                        StatusMessageTargetLocator::bottomEdge
                ))
                .orElseGet(() -> labels.stream()
                        .filter(StatusMessageTargetLocator::looksLikeStatusLabel)
                        .max(Comparator.comparingDouble(
                                StatusMessageTargetLocator::bottomEdge
                        ))
                        .orElseGet(() -> labels.stream()
                                .max(Comparator.comparingDouble(
                                        StatusMessageTargetLocator::bottomEdge
                                ))
                                .orElse(null)));

        if (label == null) {
            return Optional.empty();
        }

        return Optional.of(new Target(label, findHoverRegion(label)));
    }

    /**
     * Повертає або знаходить дані, повʼязані з «наведення».
     *
     * @param label значення, що визначає підпис для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Node findHoverRegion(Label label) {
        Node current = label.getParent();

        while (current instanceof Pane pane) {
            if (hasStatusToken(pane.getId())
                    || pane.getStyleClass().stream()
                    .anyMatch(StatusMessageTargetLocator::hasStatusToken)) {
                return pane;
            }
            current = pane.getParent();
        }

        return label;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param label значення, що визначає підпис для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    private static double bottomEdge(Label label) {
        return label.localToScene(
                label.getBoundsInLocal()
        ).getMaxY();
    }

    /**
     * Повертає результат операції «шукає стан підпис».
     *
     * @param label значення, що визначає підпис для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    private static boolean looksLikeStatusLabel(Label label) {
        return hasStatusToken(label.getId())
                || label.getStyleClass().stream()
                .anyMatch(StatusMessageTargetLocator::hasStatusToken);
    }

    /**
     * Перевіряє, чи виконується умова «стан».
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    private static boolean hasStatusToken(String value) {
        return value != null
                && value.toLowerCase().contains("status");
    }

    /**
     * Виконує операцію «відповідну операцію».
     *
     * @param node графічний вузол JavaFX.
     *
     * @param consumer значення, що визначає відповідну операцію для цієї операції.
     */
    private static void collectLabels(
            Node node,
            java.util.function.Consumer<Label> consumer
    ) {
        if (node instanceof Label label) {
            consumer.accept(label);
        }

        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                collectLabels(child, consumer);
            }
        }
    }
}
