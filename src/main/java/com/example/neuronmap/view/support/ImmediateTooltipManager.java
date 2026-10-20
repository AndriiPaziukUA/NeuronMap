package com.example.neuronmap.view.support;

import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Керує миттєвим показом підказок і їхньою клавіатурною поведінкою для всього дерева вузлів сцени.
 * Політика Escape підтримується незалежно від того, чи призупинено показ підказок.
 */
public final class ImmediateTooltipManager {

    private final Scene scene;
    private final EventHandler<MouseEvent> mouseEnteredHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;
    private final ChangeListener<Node> rootChangeListener = this::handleRootChanged;
    private final Map<Parent, ListChangeListener<Node>> childrenListeners = new IdentityHashMap<>();
    private final Map<Control, ChangeListener<Tooltip>> tooltipListeners = new IdentityHashMap<>();
    private Control activeControl;
    private boolean suspended;
    private boolean disposed;

    /** Створює менеджер і реєструє обробники показу підказок та спостереження за деревом сцени. */
    private ImmediateTooltipManager(Scene scene) {
        this.scene = scene;
        installHandlers();
    }

    /**
     * Створює менеджер миттєвих підказок і під'єднує його до сцени.
     * Усі наявні та динамічно додані елементи керування отримують спільну політику Escape.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
     * @return менеджер, який керує показом підказок на сцені.
     * @throws IllegalArgumentException якщо сцену не задано.
     */
    public static ImmediateTooltipManager install(Scene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
        return new ImmediateTooltipManager(scene);
    }

    /** Приховує активну підказку та тимчасово блокує показ нових підказок, не припиняючи стеження за ними. */
    public void pause() {
        if (disposed || suspended) {
            return;
        }
        suspended = true;
        hideActiveTooltip();
    }

    /** Дозволяє показ підказок після закриття меню; попередня підказка автоматично не відновлюється. */
    public void resume() {
        if (!disposed) {
            suspended = false;
        }
    }

    /** Від'єднує обробники й слухачі дерева сцени, приховує активну підказку та звільняє посилання. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        suspended = true;
        hideActiveTooltip();
        scene.removeEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredHandler);
        scene.removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        scene.removeEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        scene.rootProperty().removeListener(rootChangeListener);
        untrackNode(scene.getRoot());
    }

    /** Реєструє обробники миші та починає стежити за наявним і майбутнім вмістом сцени. */
    private void installHandlers() {
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredHandler);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        scene.rootProperty().addListener(rootChangeListener);
        trackNode(scene.getRoot());
    }

    /** Налаштовує підказку відразу після зміни кореневого вузла сцени. */
    private void handleRootChanged(
            javafx.beans.value.ObservableValue<? extends Node> observable,
            Node oldRoot,
            Node newRoot
    ) {
        if (oldRoot != null) {
            untrackNode(oldRoot);
        }
        if (newRoot != null) {
            trackNode(newRoot);
        }
    }

    /** Додає слухачі до вузла та рекурсивно налаштовує всі підказки в його піддереві. */
    private void trackNode(Node node) {
        if (node instanceof Control control) {
            tooltipListeners.computeIfAbsent(control, trackedControl -> {
                ChangeListener<Tooltip> listener = (observable, oldTooltip, newTooltip) -> {
                    configureTooltip(newTooltip);
                    if (newTooltip == null && activeControl == trackedControl) {
                        hideActiveTooltip();
                    }
                };
                trackedControl.tooltipProperty().addListener(listener);
                configureTooltip(trackedControl.getTooltip());
                return listener;
            });
        }

        if (node instanceof Parent parent) {
            childrenListeners.computeIfAbsent(parent, trackedParent -> {
                ListChangeListener<Node> listener = change -> {
                    while (change.next()) {
                        for (Node removed : change.getRemoved()) {
                            untrackNode(removed);
                        }
                        for (Node added : change.getAddedSubList()) {
                            trackNode(added);
                        }
                    }
                };
                trackedParent.getChildrenUnmodifiable().addListener(listener);
                for (Node child : trackedParent.getChildrenUnmodifiable()) {
                    trackNode(child);
                }
                return listener;
            });
        }
    }

    /** Прибирає слухачі вузла й усіх його нащадків, щоб не утримувати від'єднані подання в пам'яті. */
    private void untrackNode(Node node) {
        if (node instanceof Parent parent) {
            ListChangeListener<Node> listener = childrenListeners.remove(parent);
            if (listener != null) {
                parent.getChildrenUnmodifiable().removeListener(listener);
            }
            for (Node child : parent.getChildrenUnmodifiable()) {
                untrackNode(child);
            }
        }

        if (node instanceof Control control) {
            ChangeListener<Tooltip> listener = tooltipListeners.remove(control);
            if (listener != null) {
                control.tooltipProperty().removeListener(listener);
            }
            if (activeControl == control) {
                hideActiveTooltip();
            }
        }
    }

    /** Застосовує спільну клавіатурну політику незалежно від джерела створення підказки. */
    private static void configureTooltip(Tooltip tooltip) {
        if (tooltip != null) {
            TooltipFactory.configure(tooltip);
        }
    }

    /** Знаходить елемент із підказкою під вказівником і показує підказку, якщо ціль змінилася. */
    private void handleMouseMoved(MouseEvent event) {
        if (suspended || disposed) {
            hideActiveTooltip();
            return;
        }
        Control control = findTooltipControl(event.getTarget());
        if (control == null) {
            hideActiveTooltip();
            return;
        }

        Tooltip tooltip = control.getTooltip();
        if (tooltip == null) {
            hideActiveTooltip();
            return;
        }
        TooltipFactory.configure(tooltip);
        if (activeControl == control) {
            return;
        }

        hideActiveTooltip();
        activeControl = control;
        tooltip.show(control, event.getScreenX() + 10, event.getScreenY() + 18);
    }

    /** Приховує підказку, якщо вказівник залишив елемент, що її надавав. */
    private void handleMouseExited(MouseEvent event) {
        Node target = event.getTarget() instanceof Node node ? node : null;
        if (suspended || disposed || target == null || findTooltipControl(target) == null) {
            hideActiveTooltip();
        }
    }

    /** Знаходить найближчого предка-контрол, який має власну підказку. */
    private static Control findTooltipControl(Object target) {
        Node ancestor = JavaFxNodeLookup.findAncestorMatching(
                target,
                node -> node instanceof Control control && control.getTooltip() != null
        );
        return ancestor instanceof Control control ? control : null;
    }

    /** Приховує підказку й скидає посилання на її елемент керування. */
    private void hideActiveTooltip() {
        if (activeControl == null) {
            return;
        }
        Tooltip tooltip = activeControl.getTooltip();
        if (tooltip != null) {
            tooltip.hide();
        }
        activeControl = null;
    }
}
