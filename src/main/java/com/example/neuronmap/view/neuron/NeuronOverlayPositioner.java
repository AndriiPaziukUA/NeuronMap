package com.example.neuronmap.view.neuron;

import com.example.neuronmap.view.editor.WorkspaceView;
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
     * Створює позиціонер накладок, прив’язаний до полотна редактора.
     *
     * @param workspace полотно, яке надає шар накладок для меню й ручки обертання.
     * @throws IllegalArgumentException якщо полотно не задано.
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
     * Розміщує видиме меню та ручку обертання відносно поточного геометричного стану нейрона.
     *
     * @param neuronView візуальне подання нейрона, для якого позиціонуються накладки.
     * @param menu контекстне меню; якщо воно відсутнє або приховане, його положення не змінюється.
     * @param handle ручка обертання; якщо вона відсутня або прихована, її положення не змінюється.
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
     * Розміщує ручку обертання в заданій системі координат JavaFX.
     *
     * @param neuronView візуальне подання нейрона, біля якого розташовується ручка.
     * @param handle ручка обертання, яку потрібно перемістити.
     * @param coordinateSpace вузол, локальні координати якого використовує батьківський контейнер ручки.
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
     * Обчислює центр прямокутника нейрона в локальній системі координат заданого вузла.
     *
     * @param neuronView візуальне подання, геометричний центр якого потрібно знайти.
     * @param coordinateSpace вузол JavaFX, відносно якого повертаються координати.
     * @return центр меж нейрона в локальних координатах {@code coordinateSpace}.
     * @throws IllegalArgumentException якщо подання нейрона або вузол системи координат не задано.
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
     * Обчислює центр ручки обертання в локальній системі координат заданого батьківського вузла.
     *
     * @param neuronView візуальне подання нейрона, для якого визначається положення ручки.
     * @param coordinateSpace вузол JavaFX, локальні координати якого потрібні результату.
     * @return центр ручки обертання в локальних координатах {@code coordinateSpace}.
     * @throws IllegalArgumentException якщо подання нейрона або вузол системи координат не задано.
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
     * Обчислює точку, зміщену від опори вздовж напрямку від неї до другої точки.
     *
     * @param anchor опорна точка, від якої відраховується зміщення.
     * @param directionPoint друга точка, що задає напрямок зміщення від опори.
     * @param distance невід’ємна скінченна відстань від опорної точки до результату.
     * @return точка на заданій відстані від {@code anchor} у напрямку {@code directionPoint}.
     * @throws IllegalArgumentException якщо точку не задано, відстань некоректна або точки збігаються.
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
     * Визначає центр ручки обертання в координатах сцени з урахуванням повороту й рамки виділення нейрона.
     *
     * @param neuronView візуальне подання, до якого належить ручка обертання.
     * @return центр ручки обертання в координатах сцени.
     */
    private static Point2D handleCenterInScene(NeuronView neuronView) {
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
     * Перетворює локальну точку подання нейрона на координати переданого вузла JavaFX.
     *
     * @param neuronView подання, у локальному просторі якого задано точку.
     * @param localX горизонтальна координата точки в локальному просторі нейрона.
     * @param localY вертикальна координата точки в локальному просторі нейрона.
     * @param coordinateSpace вузол, у локальному просторі якого потрібен результат.
     * @return та сама геометрична точка в локальних координатах {@code coordinateSpace}.
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
     * Розміщує контекстне меню над верхньою межею нейрона з урахуванням відстані між ними.
     *
     * @param menu контекстне меню, розміри якого визначають потрібне положення.
     * @param rectangleCenter центр прямокутника нейрона в системі координат шару накладок.
     * @param rectangleTopY найменша вертикальна координата меж нейрона в тому самому просторі.
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
     * Знаходить верхню координату обмежувального прямокутника нейрона в просторі шару накладок.
     *
     * @param neuronView подання нейрона, чотири кути якого потрібно перетворити.
     * @param overlay контейнер накладок, у локальній системі координат якого виконується розрахунок.
     * @return найменша вертикальна координата серед чотирьох перетворених кутів прямокутника.
     */
    private double rectangleTopYInParent(NeuronView neuronView, Pane overlay) {
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
