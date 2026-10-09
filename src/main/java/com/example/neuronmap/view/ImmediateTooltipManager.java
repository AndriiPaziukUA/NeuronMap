package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

/**
 * Показує підказку без стандартної затримки JavaFX і приховує її, коли курсор залишає відповідний елемент.
 */
public final class ImmediateTooltipManager {

    private final Scene scene;
    private final EventHandler<MouseEvent> mouseEnteredHandler =
            this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseMovedHandler =
            this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler =
            this::handleMouseExited;
    private Control activeControl;

    /**
     * Створює екземпляр ImmediateTooltipManager та зберігає передані залежності, потрібні для його роботи.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
     */
    private ImmediateTooltipManager(Scene scene) {
        this.scene = scene;
        installHandlers();
    }

    /**
     * Створює менеджер миттєвих підказок і під’єднує його до сцени.
     *
     * @param scene сцена JavaFX, до якої приєднують компонент.
     */
    public static ImmediateTooltipManager install(Scene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
        return new ImmediateTooltipManager(scene);
    }

    /**
     * Від’єднує обробники подій і звільняє ресурси, якими керує компонент.
     */
    public void dispose() {
        hideActiveTooltip();
        scene.removeEventFilter(
                MouseEvent.MOUSE_ENTERED_TARGET,
                mouseEnteredHandler
        );
        scene.removeEventFilter(
                MouseEvent.MOUSE_MOVED,
                mouseMovedHandler
        );
        scene.removeEventFilter(
                MouseEvent.MOUSE_EXITED,
                mouseExitedHandler
        );
    }

    /**
     * Реєструє обробники подій, потрібні для handlers.
     */
    private void installHandlers() {
        scene.addEventFilter(
                MouseEvent.MOUSE_ENTERED_TARGET,
                mouseEnteredHandler
        );
        scene.addEventFilter(
                MouseEvent.MOUSE_MOVED,
                mouseMovedHandler
        );
        scene.addEventFilter(
                MouseEvent.MOUSE_EXITED,
                mouseExitedHandler
        );
    }

    /**
     * Визначає елемент під курсором і за потреби показує його підказку.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
     */
    private void handleMouseMoved(MouseEvent event) {
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

        configureTooltip(tooltip);

        if (activeControl == control) {
            return;
        }

        hideActiveTooltip();
        activeControl = control;
        tooltip.show(
                control,
                event.getScreenX() + 10,
                event.getScreenY() + 18
        );
    }

    /**
     * Приховує активну підказку після виходу курсора з її цільового елемента.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
     */
    private void handleMouseExited(MouseEvent event) {
        Node target = event.getTarget() instanceof Node node
                ? node
                : null;

        if (target == null || findTooltipControl(target) == null) {
            hideActiveTooltip();
        }
    }

    /**
     * Налаштовує tooltip для роботи з відповідним елементом інтерфейсу.
     *
     * @param tooltip значення «tooltip», яке використовується в цьому методі.
     */
    private void configureTooltip(Tooltip tooltip) {
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
    }

    /**
     * Приховує active tooltip і завершує пов’язаний стан відображення.
     */
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

    /**
     * Знаходить елемент керування JavaFX, що відповідає за підказку під курсором.
     *
     * @param target цільовий вузол або об’єкт інтерфейсу, який потрібно перевірити чи знайти.
     */
    private static Control findTooltipControl(Object target) {
        Node node = target instanceof Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (node instanceof Control control
                    && control.getTooltip() != null) {
                return control;
            }
            node = node.getParent();
        }

        return null;
    }
}
