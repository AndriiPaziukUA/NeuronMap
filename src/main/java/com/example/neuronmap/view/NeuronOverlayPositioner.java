package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Обчислює розташування контекстного меню та ручки обертання відносно повернутого нейрона й координатного простору JavaFX.
 */
public final class NeuronOverlayPositioner {

    public static final double MENU_GAP = 8.0;
    public static final double HANDLE_GAP = 8.0;

    private static final double NORMAL_SAMPLE = 1.0;

    private final WorkspaceView workspace;

    /**
     * Створює екземпляр NeuronOverlayPositioner та зберігає передані залежності, потрібні для його роботи.
     *
     * @param workspace полотно редактора.
     */
    public NeuronOverlayPositioner(WorkspaceView workspace) {
        if (workspace == null) {

            throw new IllegalArgumentException(
                    "workspace must not be null"
            );
        }
        this.workspace = workspace;
    }

/**
 * Обчислює положення контекстного меню й ручки обертання відносно видимого прямокутника нейрона.
 *
 * @param neuronView візуальне подання нейрона.
 * @param menu контекстне меню або сторінка меню.
 * @param handle ручка обертання нейрона.
 */
public void position(
            NeuronView neuronView,
            VBox menu,
            RotationHandleView handle
    ) {
        if (neuronView == null) {
            return;
        }

        Pane overlay = workspace.overlayLayer();

        Point2D center = toParent(
                neuronView,
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0,
                overlay
        );

        if (handle != null && handle.isVisible()) {
            Point2D handleCenter = handleCenterInParent(
                    neuronView,
                    overlay
            );

            handle.placeCenterAt(handleCenter);
            handle.setVisualRotation(neuronView.getRotate());
        }

        if (menu != null && menu.isVisible()) {
            positionMenu(
                    menu,
                    center,
                    rectangleTopYInParent(neuronView, overlay)
            );
        }
    }

/**
 * Перетворює геометрію нейрона в координати батьківського вузла та розміщує поруч ручку обертання.
 *
 * @param neuronView візуальне подання нейрона.
 * @param handle ручка обертання нейрона.
 * @param coordinateSpace вузол JavaFX, відносно якого обчислюються координати.
 */
public static void positionRotationHandleInParent(
            NeuronView neuronView,
            RotationHandleView handle,
            Node coordinateSpace
    ) {
        if (neuronView == null
                || handle == null
                || coordinateSpace == null) {
            return;
        }

        Point2D sceneHandleCenter = handleCenterInScene(neuronView);
        Point2D parentCenter = coordinateSpace.sceneToLocal(
                sceneHandleCenter
        );

        handle.placeCenterAt(parentCenter);
        handle.setVisualRotation(neuronView.getRotate());
    }

/**
 * Повертає центр меж нейрона в координатах переданого вузла JavaFX.
 *
 * @param neuronView візуальне подання нейрона.
 * @param coordinateSpace вузол JavaFX, відносно якого обчислюються координати.
 *
 * @return центр меж нейрона в координатах переданого вузла JavaFX.
 */
public Point2D rectangleCenterIn(
            NeuronView neuronView,
            Node coordinateSpace
    ) {
        if (neuronView == null) {

            throw new IllegalArgumentException(
                    "neuronView must not be null"
            );
        }
        if (coordinateSpace == null) {

            throw new IllegalArgumentException(
                    "coordinateSpace must not be null"
            );
        }

        return toParent(
                neuronView,
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT / 2.0,
                coordinateSpace
        );
    }

/**
 * Обробляє подію «center in parent» і передає її до відповідної операції редактора.
 *
 * @param neuronView візуальне подання нейрона.
 * @param coordinateSpace вузол JavaFX, відносно якого обчислюються координати.
 */
public Point2D handleCenterInParent(
            NeuronView neuronView,
            Node coordinateSpace
    ) {
        if (neuronView == null) {

            throw new IllegalArgumentException(
                    "neuronView must not be null"
            );
        }
        if (coordinateSpace == null) {

            throw new IllegalArgumentException(
                    "coordinateSpace must not be null"
            );
        }

        return coordinateSpace.sceneToLocal(
                handleCenterInScene(neuronView)
        );
    }

/**
 * Обчислює точку на заданій відстані вздовж нормалі до напрямку між двома точками.
 *
 * @param anchor опорна точка, відносно якої розраховують положення.
 * @param directionPoint значення «direction point», яке використовується в цьому методі.
 * @param distance значення «distance», яке використовується в цьому методі.
 */
static Point2D offsetAlongNormal(
            Point2D anchor,
            Point2D directionPoint,
            double distance
    ) {
        if (anchor == null) {

            throw new IllegalArgumentException(
                    "anchor must not be null"
            );
        }
        if (directionPoint == null) {

            throw new IllegalArgumentException(
                    "directionPoint must not be null"
            );
        }
        if (!Double.isFinite(distance) || distance < 0.0) {

            throw new IllegalArgumentException(
                    "distance must be finite and non-negative"
            );
        }

        double dx = directionPoint.getX() - anchor.getX();
        double dy = directionPoint.getY() - anchor.getY();
        double length = Math.hypot(dx, dy);

        if (length == 0.0) {

            throw new IllegalArgumentException(
                    "anchor and directionPoint must differ"
            );
        }

        return new Point2D(
                anchor.getX() + dx / length * distance,
                anchor.getY() + dy / length * distance
        );
    }

    /**
     * Обробляє подію «center in scene» і передає її до відповідної операції редактора.
     *
     * @param neuronView візуальне подання нейрона.
     */
    private static Point2D handleCenterInScene(
            NeuronView neuronView
    ) {
        Point2D bottomCenter = neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT
        );

        Point2D normalSample = neuronView.localToScene(
                NeuronView.WIDTH / 2.0,
                NeuronView.HEIGHT + NORMAL_SAMPLE
        );

        double sceneUnitsPerLocalUnit =
                bottomCenter.distance(normalSample) / NORMAL_SAMPLE;
        double reservedSelectionStroke =
                NeuronView.SELECTED_STROKE_WIDTH / 2.0
                        * sceneUnitsPerLocalUnit;

        double centerOffset =
                HANDLE_GAP
                        + RotationHandleView.SIZE / 2.0
                        + reservedSelectionStroke;

        return offsetAlongNormal(
                bottomCenter,
                normalSample,
                centerOffset
        );
    }

    /**
     * Перетворює локальну точку нейрона в координати переданого батьківського вузла JavaFX.
     *
     * @param neuronView візуальне подання нейрона.
     * @param localX значення «local x», яке використовується в цьому методі.
     * @param localY значення «local y», яке використовується в цьому методі.
     * @param coordinateSpace вузол JavaFX, відносно якого обчислюються координати.
     */
    private Point2D toParent(
            NeuronView neuronView,
            double localX,
            double localY,
            Node coordinateSpace
    ) {
        Point2D scenePoint = neuronView.localToScene(
                localX,
                localY
        );
        return coordinateSpace.sceneToLocal(scenePoint);
    }

    /**
     * Розміщує контекстне меню поруч із нейроном з урахуванням центра та верхньої межі його подання.
     *
     * @param menu контекстне меню або сторінка меню.
     * @param rectangleCenter значення «rectangle center», яке використовується в цьому методі.
     * @param rectangleTopY значення «rectangle top y», яке використовується в цьому методі.
     */
    private void positionMenu(
            VBox menu,
            Point2D rectangleCenter,
            double rectangleTopY
    ) {
        menu.applyCss();
        menu.autosize();

        double width = menu.getWidth();
        double height = menu.getHeight();

        if (width <= 0.0) {
            width = menu.prefWidth(-1);
        }
        if (height <= 0.0) {
            height = menu.prefHeight(-1);
        }

        menu.setManaged(false);
        menu.resize(width, height);
        menu.relocate(
                rectangleCenter.getX() - width / 2.0,
                rectangleTopY - height - MENU_GAP
        );
    }

    /**
     * Повертає вертикальну координату верхнього краю нейрона в системі координат батьківського вузла.
     *
     * @param neuronView візуальне подання нейрона.
     * @param overlay значення «overlay», яке використовується в цьому методі.
     *
     * @return вертикальну координату верхнього краю нейрона в системі координат батьківського вузла.
     */
    private double rectangleTopYInParent(
            NeuronView neuronView,
            Pane overlay
    ) {
        double topLeft = toParent(
                neuronView,
                0.0,
                0.0,
                overlay
        ).getY();
        double topRight = toParent(
                neuronView,
                NeuronView.WIDTH,
                0.0,
                overlay
        ).getY();
        double bottomLeft = toParent(
                neuronView,
                0.0,
                NeuronView.HEIGHT,
                overlay
        ).getY();
        double bottomRight = toParent(
                neuronView,
                NeuronView.WIDTH,
                NeuronView.HEIGHT,
                overlay
        ).getY();

        return Math.min(
                Math.min(topLeft, topRight),
                Math.min(bottomLeft, bottomRight)
        );
    }
}
