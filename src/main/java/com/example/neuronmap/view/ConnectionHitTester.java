package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.shape.Shape;

import java.util.Objects;

/**
 * Знаходить подання зв’язку, видима геометрія якого містить точку сцени.
 */
public final class ConnectionHitTester {

    private final WorkspaceView workspace;

    /**
     * Створює перевірювач влучання для полотна редактора.
     *
     * @param workspace полотно, на якому потрібно шукати зв’язки.
     */
    public ConnectionHitTester(WorkspaceView workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /**
     * Повертає найверхніше видиме подання зв’язку під заданою точкою сцени.
     *
     * @param sceneX горизонтальна координата точки у системі координат сцени JavaFX.
     * @param sceneY вертикальна координата точки у системі координат сцени JavaFX.
     * @return знайдене подання зв’язку або {@code null}, якщо точка не перетинає видиму геометрію.
     */
    public ConnectionView findConnectionViewAt(double sceneX, double sceneY) {
        Point2D scenePoint = new Point2D(sceneX, sceneY);

        for (int index = workspace.edgeLayer().getChildren().size() - 1;
             index >= 0;
             index--) {
            Node node = workspace.edgeLayer().getChildren().get(index);
            if (!(node instanceof ConnectionView connectionView)) {
                continue;
            }

            if (containsVisibleShape(connectionView, scenePoint)) {
                return connectionView;
            }
        }

        return null;
    }

    /**
     * Перевіряє, чи містить видима форма вузла задану точку сцени.
     *
     * @param node вузол JavaFX, який потрібно перевірити.
     * @param scenePoint точка у координатах сцени JavaFX.
     * @return {@code true}, якщо видима форма містить точку; інакше {@code false}.
     */
    private static boolean containsVisibleShape(Node node, Point2D scenePoint) {
        if (!node.isVisible()) {
            return false;
        }

        if (node instanceof Shape shape) {
            Point2D localPoint = shape.sceneToLocal(scenePoint);
            if (shape.contains(localPoint)) {
                return true;
            }
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsVisibleShape(child, scenePoint)) {
                    return true;
                }
            }
        }

        return false;
    }
}
