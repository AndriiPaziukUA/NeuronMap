package com.example.neuronmap.view;

import javafx.scene.Node;

/**
 * Надає допоміжний пошук предка JavaFX заданого типу в дереві вузлів подання.
 */
public final class JavaFxNodeLookup {

    /**
     * Забороняє створення екземплярів класу зі статичними методами пошуку.
     */
    private JavaFxNodeLookup() {
    }

    /**
     * Підіймається від цільового вузла до його предків і повертає найближчий вузол заданого типу.
     *
     * @param target вузол JavaFX, від якого починається пошук; інші об’єкти не підтримуються.
     * @param type клас вузлів, який потрібно знайти.
     * @param <T> тип шуканого вузла JavaFX.
     * @return перший вузол типу {@code type}, починаючи з {@code target}, або {@code null}, якщо його немає.
     */
    public static <T extends Node> T findAncestor(Object target, Class<T> type) {
        Node node = target instanceof Node targetNode ? targetNode : null;

        while (node != null) {
            if (type.isInstance(node)) {
                return type.cast(node);
            }
            node = node.getParent();
        }

        return null;
    }
}
