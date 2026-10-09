package com.example.neuronmap.controller;

import javafx.scene.Node;

/**
 * Допомагає знаходити графічні вузли JavaFX за потрібними ознаками.
 */
public final class JavaFxNodeLookup {

    /**
     * Повертає результат операції «вузол пошук».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private JavaFxNodeLookup() {
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     *
     * @param type тип обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static <T extends Node> T findAncestor(
            Object target,
            Class<T> type
    ) {
        Node node = target instanceof Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (type.isInstance(node)) {
                return type.cast(node);
            }
            node = node.getParent();
        }

        return null;
    }
}
