package com.example.neuronmap.controller;

import javafx.scene.Node;

/** Small JavaFX tree lookup shared by controllers handling workspace events. */
public final class JavaFxNodeLookup {

    private JavaFxNodeLookup() {
    }

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
