package com.example.neuronmap.view;

import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Перевіряє пошук найближчого предка заданого типу або за умовою в дереві вузлів JavaFX.
 */
final class JavaFxNodeLookupTest {

    /**
     * Повертає найближчу панель-предка, а не більш віддалену панель у дереві вузлів.
     */
    @Test
    void findsNearestAncestorOfRequestedType() {
        Pane root = new Pane();
        Pane nearestPane = new Pane();
        Rectangle target = new Rectangle(10.0, 10.0);
        root.getChildren().add(nearestPane);
        nearestPane.getChildren().add(target);

        assertSame(nearestPane, JavaFxNodeLookup.findAncestor(target, Pane.class));
    }

    /**
     * Повертає найближчого предка, що відповідає переданій умові, навіть якщо збіг має конкретний тип.
     */
    @Test
    void findsNearestAncestorMatchingCondition() {
        Pane root = new Pane();
        Pane nearestPane = new Pane();
        Rectangle target = new Rectangle(10.0, 10.0);
        root.getChildren().add(nearestPane);
        nearestPane.getChildren().add(target);

        assertSame(
                nearestPane,
                JavaFxNodeLookup.findAncestorMatching(target, node -> node instanceof Pane)
        );
    }

    /**
     * Повертає {@code null}, якщо переданий об’єкт не є вузлом JavaFX.
     */
    @Test
    void returnsNullWhenTargetIsNotJavaFxNode() {
        assertNull(JavaFxNodeLookup.findAncestor(new Object(), Pane.class));
    }

    /**
     * Повертає {@code null}, якщо серед цільового вузла та його предків немає потрібного типу.
     */
    @Test
    void returnsNullWhenNoAncestorMatchesRequestedType() {
        Rectangle target = new Rectangle(10.0, 10.0);

        assertNull(JavaFxNodeLookup.findAncestor(target, Pane.class));
    }

    /**
     * Повертає {@code null}, якщо жоден предок не відповідає переданій умові.
     */
    @Test
    void returnsNullWhenNoAncestorMatchesCondition() {
        Rectangle target = new Rectangle(10.0, 10.0);

        assertNull(JavaFxNodeLookup.findAncestorMatching(target, node -> node instanceof Pane));
    }
}
