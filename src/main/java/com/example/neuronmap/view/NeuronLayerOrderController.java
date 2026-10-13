package com.example.neuronmap.view;

import com.example.neuronmap.model.Connection;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Керує порядком відображення нейронів і пов’язаних із ними зв’язків у шарах JavaFX.
 * Компонент змінює лише порядок дочірніх вузлів і не обробляє користувацькі події.
 */
public final class NeuronLayerOrderController {

    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Supplier<Iterable<Connection>> connections;

    /**
     * Створює компонент керування порядком шарів і зберігає залежності для пошуку подань.
     *
     * @param neuronViews мапа подань нейронів за їхніми ідентифікаторами.
     * @param connectionViews мапа подань зв’язків за їхніми ідентифікаторами.
     * @param connections постачальник зв’язків поточної моделі.
     */
    public NeuronLayerOrderController(
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Supplier<Iterable<Connection>> connections
    ) {
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.connectionViews = Objects.requireNonNull(
                connectionViews,
                "connectionViews"
        );
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /**
     * Переміщує нейрон і всі пов’язані з ним зв’язки в кінець списків дочірніх вузлів їхніх шарів.
     *
     * @param neuronId ідентифікатор нейрона, який потрібно показати поверх інших нейронів.
     */
    public void bringNeuronToFront(String neuronId) {
        if (neuronId == null) {
            return;
        }

        NeuronView neuronView = neuronViews.get(neuronId);
        moveToEnd(neuronView);

        for (Connection connection : connections.get()) {
            if (!neuronId.equals(connection.sourceId())
                    && !neuronId.equals(connection.targetId())) {
                continue;
            }

            ConnectionView connectionView = connectionViews.get(connection.id());
            moveToEnd(connectionView);
        }
    }

    /**
     * Переміщує вузол у кінець списку дочірніх вузлів батьківської панелі, якщо він у ній розміщений.
     *
     * @param node вузол JavaFX, який потрібно перемістити.
     */
    private static void moveToEnd(Node node) {
        if (node == null || !(node.getParent() instanceof Pane layer)) {
            return;
        }

        var children = layer.getChildren();
        if (children.size() <= 1 || children.get(children.size() - 1) == node) {
            return;
        }

        children.remove(node);
        children.add(node);
    }
}
