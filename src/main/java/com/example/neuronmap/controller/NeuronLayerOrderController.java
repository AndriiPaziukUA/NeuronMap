package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Змінює порядок відображення нейронів у графічному шарі.
 */
public final class NeuronLayerOrderController {

    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, ConnectionView> connectionViews;
    private final Supplier<Iterable<Connection>> connections;

    /**
     * Повертає результат операції «нейрон».
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param connectionViews значення, що визначає звʼязок для цієї операції.
     *
     * @param connections значення, що визначає звʼязки для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronLayerOrderController(
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Supplier<Iterable<Connection>> connections
    ) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.connectionViews = Objects.requireNonNull(
                connectionViews,
                "connectionViews"
        );
        this.connections = Objects.requireNonNull(connections, "connections");
    }

/**
 * Виконує операцію «нейрон до».
 *
 * @param neuronId ідентифікатор нейрона.
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

            ConnectionView connectionView =
                    connectionViews.get(connection.id());
            moveToEnd(connectionView);
        }
    }

    /**
     * Переміщує обʼєкт «до» відповідно до переданого зміщення.
     *
     * @param node графічний вузол JavaFX.
     */
    private static void moveToEnd(Node node) {
        if (node == null || !(node.getParent() instanceof Pane layer)) {
            return;
        }

        var children = layer.getChildren();
        if (children.size() <= 1
                || children.get(children.size() - 1) == node) {
            return;
        }

        children.remove(node);
        children.add(node);
    }
}
