package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

/** Shows every installed tooltip immediately and consistently. */
public final class ImmediateTooltipManager {

    private final Scene scene;
    private final EventHandler<MouseEvent> mouseEnteredHandler =
            this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseMovedHandler =
            this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler =
            this::handleMouseExited;
    private Control activeControl;

    private ImmediateTooltipManager(Scene scene) {
        this.scene = scene;
        installHandlers();
    }

    public static ImmediateTooltipManager install(Scene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
        return new ImmediateTooltipManager(scene);
    }

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

    private void handleMouseExited(MouseEvent event) {
        Node target = event.getTarget() instanceof Node node
                ? node
                : null;

        if (target == null || findTooltipControl(target) == null) {
            hideActiveTooltip();
        }
    }

    private void configureTooltip(Tooltip tooltip) {
        tooltip.setShowDelay(UiTiming.TOOLTIP_SHOW_DELAY);
        tooltip.setShowDuration(UiTiming.TOOLTIP_VISIBLE_DURATION);
        tooltip.setHideDelay(UiTiming.TOOLTIP_HIDE_DELAY);
    }

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
