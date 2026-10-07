package com.example.neuronmap.view;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds the real status label after the view has received its message. */
public final class StatusMessageTargetLocator {

    public record Target(Label label, Node hoverRegion) {
    }

    private StatusMessageTargetLocator() {
    }

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

    private static double bottomEdge(Label label) {
        return label.localToScene(
                label.getBoundsInLocal()
        ).getMaxY();
    }

    private static boolean looksLikeStatusLabel(Label label) {
        return hasStatusToken(label.getId())
                || label.getStyleClass().stream()
                .anyMatch(StatusMessageTargetLocator::hasStatusToken);
    }

    private static boolean hasStatusToken(String value) {
        return value != null
                && value.toLowerCase().contains("status");
    }

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
