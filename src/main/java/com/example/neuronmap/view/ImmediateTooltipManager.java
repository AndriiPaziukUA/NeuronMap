package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

/**
 * Керує негайним показом і приховуванням підказок інтерфейсу.
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
     * Повертає результат операції «підказка».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private ImmediateTooltipManager(Scene scene) {
        this.scene = scene;
        installHandlers();
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param scene значення, що визначає сцена для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static ImmediateTooltipManager install(Scene scene) {
        if (scene == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("scene must not be null");
        }
        /**
         * Повертає результат операції «підказка».
         *
         * @param scene значення, що визначає сцена для цієї операції.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new ImmediateTooltipManager(scene);
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
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
     * Виконує операцію «відповідну операцію».
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
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
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
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
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
     * Задає або оновлює значення, повʼязані з «підказка».
     *
     * @param tooltip значення, що визначає підказка для цієї операції.
     */
    private void configureTooltip(Tooltip tooltip) {
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
    }

    /**
     * Виконує операцію «приховати підказка».
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
     * Повертає або знаходить дані, повʼязані з «підказка».
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
