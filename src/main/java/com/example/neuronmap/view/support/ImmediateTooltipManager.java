package com.example.neuronmap.view.support;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
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
     * Створює менеджер і відразу реєструє обробники подій на переданій сцені.
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
     * @return менеджер, який керує показом підказок на сцені.
     * @throws IllegalArgumentException якщо сцену не задано.
     */
    public static ImmediateTooltipManager install(Scene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
        return new ImmediateTooltipManager(scene);
    }

    /**
     * Від’єднує обробники подій і приховує активну підказку, звільняючи ресурси компонента.
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
     * Реєструє обробники входу, руху та виходу вказівника, потрібні для миттєвого показу підказок.
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
     * Знаходить елемент із підказкою під вказівником і показує підказку, якщо ціль змінилася.
     *
     * @param event подія миші, що містить цільовий вузол і координати екрана.
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

        TooltipFactory.configure(tooltip);

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
     * Приховує активну підказку, якщо вказівник вийшов за межі елемента, що її надає.
     *
     * @param event подія виходу миші з цільового вузла.
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
     * Знаходить найближчого предка з типом {@link Control}, який має власну підказку.
     * Обхід дерева вузлів виконує спільний {@link JavaFxNodeLookup}, щоб не дублювати механізм пошуку.
     *
     * @param target цільовий вузол події або інший об’єкт, переданий JavaFX.
     * @return найближчий елемент керування з підказкою або {@code null}, якщо такого немає.
     */
    private static Control findTooltipControl(Object target) {
        Node ancestor = JavaFxNodeLookup.findAncestorMatching(
                target,
                node -> node instanceof Control control && control.getTooltip() != null
        );
        return ancestor instanceof Control control ? control : null;
    }

    /**
     * Приховує активну підказку й скидає посилання на її елемент керування.
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
}
