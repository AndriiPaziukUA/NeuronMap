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
 * Знаходить у JavaFX-сцені мітку повідомлення стану та область наведення, що керує її видимістю.
 */
public final class StatusMessageTargetLocator {

    /**
     * Зберігає мітку повідомлення стану та область інтерфейсу, наведення на яку впливає на її видимість.
     * @param label мітка JavaFX, у якій показують повідомлення.
     * @param hoverRegion вузол, за наведенням на який відстежують взаємодію з повідомленням.
     */
    public record Target(Label label, Node hoverRegion) {
    }

    private StatusMessageTargetLocator() {
    }

    /**
     * Знаходить мітку повідомлення, текст якої відповідає заданому значенню.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
     * @param text текст, який потрібно показати або розібрати.
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
     * Знаходить hover region за заданими координатами або критеріями пошуку.
     *
     * @param label значення «label», яке використовується в цьому методі.
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
     * Повертає координату нижнього краю мітки в системі координат сцени JavaFX.
     *
     * @param label значення «label», яке використовується в цьому методі.
     *
     * @return координату нижнього краю мітки в системі координат сцени JavaFX.
     */
    private static double bottomEdge(Label label) {
        return label.localToScene(
                label.getBoundsInLocal()
        ).getMaxY();
    }

    /**
     * Перевіряє ідентифікатор та CSS-класи мітки, щоб визначити, чи призначена вона для повідомлення стану.
     *
     * @param label значення «label», яке використовується в цьому методі.
     */
    private static boolean looksLikeStatusLabel(Label label) {
        return hasStatusToken(label.getId())
                || label.getStyleClass().stream()
                .anyMatch(StatusMessageTargetLocator::hasStatusToken);
    }

    /**
     * Перевіряє, чи містить стиль або ідентифікатор елемента маркер рядка стану.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    private static boolean hasStatusToken(String value) {
        return value != null
                && value.toLowerCase().contains("status");
    }

    /**
     * Рекурсивно обходить вузли сцени й передає кожну знайдену мітку до callback.
     *
     * @param node вузол JavaFX, який потрібно перевірити або змінити.
     * @param consumer callback, що приймає результат операції.
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
