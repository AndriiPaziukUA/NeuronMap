package com.example.neuronmap.view.support;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;

/** Показує підказки без стандартної затримки JavaFX та керує їхнім життєвим циклом у межах сцени. */
public final class ImmediateTooltipManager {

    private final Scene scene;
    private final EventHandler<MouseEvent> mouseEnteredHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;
    private Control activeControl;
    private boolean suspended;
    private boolean disposed;

    /** Створює менеджер і відразу реєструє обробники подій на переданій сцені. */
    private ImmediateTooltipManager(Scene scene) {
        this.scene = scene;
        installHandlers();
    }

    /**
     * Створює менеджер миттєвих підказок і під'єднує його до сцени.
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

    /** Приховує активну підказку та тимчасово блокує показ нових підказок. */
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

    /** Від'єднує обробники подій і приховує активну підказку, звільняючи ресурси компонента. */
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
    }

    /** Реєструє обробники входу, руху та виходу вказівника, потрібні для миттєвого показу підказок. */
    private void installHandlers() {
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredHandler);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
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

    /** Приховує активну підказку й скидає посилання на її елемент керування. */
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
