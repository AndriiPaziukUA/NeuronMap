package com.example.neuronmap.controller;

import javafx.scene.Node;

/**
 * Містить допоміжний пошук вузла JavaFX потрібного типу серед предків переданого об’єкта.
 */
public final class JavaFxNodeLookup {

    private JavaFxNodeLookup() {
    }

    /**
     * Підіймається від переданого об’єкта до предків JavaFX і повертає першого вузла заданого типу.
     *
     * @param target цільовий вузол або об’єкт інтерфейсу, який потрібно перевірити чи знайти.
     * @param type тип нейрона або елемента.
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
