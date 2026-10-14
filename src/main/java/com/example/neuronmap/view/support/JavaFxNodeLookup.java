package com.example.neuronmap.view.support;

import javafx.scene.Node;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Надає допоміжний пошук предка JavaFX заданого типу або за умовою в дереві вузлів подання.
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
        Objects.requireNonNull(type, "type");
        Node ancestor = findAncestorMatching(target, type::isInstance);
        return ancestor == null ? null : type.cast(ancestor);
    }

    /**
     * Підіймається від цільового вузла до його предків і повертає найближчий вузол, що відповідає умові.
     *
     * @param target вузол JavaFX, від якого починається пошук; інші об’єкти не підтримуються.
     * @param condition умова, якій має відповідати шуканий вузол.
     * @return найближчий вузол, для якого {@code condition} повертає {@code true}, або {@code null}, якщо збігу немає.
     */
    public static Node findAncestorMatching(Object target, Predicate<? super Node> condition) {
        Objects.requireNonNull(condition, "condition");
        Node node = target instanceof Node targetNode ? targetNode : null;

        while (node != null) {
            if (condition.test(node)) {
                return node;
            }
            node = node.getParent();
        }

        return null;
    }
}
