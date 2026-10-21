package com.example.neuronmap.view.editor;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Керує порядком відображення нейронів і пов’язаних із ними зв’язків у шарах JavaFX.
 * Компонент змінює лише порядок дочірніх вузлів і не обробляє користувацькі події.
 */
public final class NeuronLayerOrderManager {

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
    public NeuronLayerOrderManager(
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
     * Переміщує вузол на передній план без зміни його належності до батьківського контейнера.
     * Це зберігає активні анімації, які залежать від життєвого циклу подання зв’язку.
     *
     * @param node вузол JavaFX, який потрібно показати поверх інших дочірніх вузлів.
     */
    private static void moveToEnd(Node node) {
        if (node == null || !(node.getParent() instanceof Pane)) {
            return;
        }
        node.toFront();
    }
}
