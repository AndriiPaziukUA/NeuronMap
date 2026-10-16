package com.example.neuronmap.view.window;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Створює векторні значки кнопок оформлення вікна без залежності від стану вікна чи життєвого циклу подання.
 */
final class WindowChromeIconFactory {

    /**
     * Забороняє створення екземплярів утилітного компонента, який надає лише статичні фабричні методи.
     */
    private WindowChromeIconFactory() {
    }

    /**
     * Створює значок згортання у вигляді короткої горизонтальної лінії.
     *
     * @return графічний вузол значка згортання.
     */
    static Node createMinimizeIcon() {
        Line line = new Line(3.0, 8.0, 13.0, 8.0);
        line.getStyleClass().add("window-icon-stroke");
        line.setStrokeWidth(1.2);
        return iconCanvas(line);
    }

    /**
     * Створює значок переходу в повноекранний режим або виходу з нього для кнопки macOS.
     *
     * @param fullScreen чи вже перебуває вікно в повноекранному режимі.
     * @return значок із чотирьох кутів, які спрямовані назовні або всередину.
     */
    static Node createMacOsFullScreenIcon(boolean fullScreen) {
        double nearEdge = fullScreen ? 5.0 : 2.0;
        double farEdge = fullScreen ? 11.0 : 14.0;
        double cornerLength = fullScreen ? 2.0 : 3.0;

        Line topLeftHorizontal = new Line(nearEdge, nearEdge, nearEdge + cornerLength, nearEdge);
        Line topLeftVertical = new Line(nearEdge, nearEdge, nearEdge, nearEdge + cornerLength);
        Line topRightHorizontal = new Line(farEdge - cornerLength, nearEdge, farEdge, nearEdge);
        Line topRightVertical = new Line(farEdge, nearEdge, farEdge, nearEdge + cornerLength);
        Line bottomLeftHorizontal = new Line(nearEdge, farEdge, nearEdge + cornerLength, farEdge);
        Line bottomLeftVertical = new Line(nearEdge, farEdge - cornerLength, nearEdge, farEdge);
        Line bottomRightHorizontal = new Line(farEdge - cornerLength, farEdge, farEdge, farEdge);
        Line bottomRightVertical = new Line(farEdge, farEdge - cornerLength, farEdge, farEdge);

        for (Line line : new Line[]{
                topLeftHorizontal, topLeftVertical,
                topRightHorizontal, topRightVertical,
                bottomLeftHorizontal, bottomLeftVertical,
                bottomRightHorizontal, bottomRightVertical
        }) {
            line.getStyleClass().add("window-icon-stroke");
            line.setStrokeWidth(1.2);
        }

        return iconCanvas(
                topLeftHorizontal, topLeftVertical,
                topRightHorizontal, topRightVertical,
                bottomLeftHorizontal, bottomLeftVertical,
                bottomRightHorizontal, bottomRightVertical
        );
    }

    /**
     * Створює значок максимізації або відновлення з одного чи двох прямокутників.
     *
     * @param restore чи потрібно показати накладені прямокутники режиму відновлення.
     * @return графічний вузол значка максимізації або відновлення.
     */
    static Node createMaximizeIcon(boolean restore) {
        Rectangle front = new Rectangle(3.0, restore ? 5.0 : 3.0, 10.0, 10.0);
        front.setFill(Color.TRANSPARENT);
        front.getStyleClass().add("window-icon-outline");
        front.setStrokeWidth(1.2);
        if (!restore) {
            return iconCanvas(front);
        }

        Rectangle back = new Rectangle(5.0, 3.0, 9.0, 9.0);
        back.setFill(Color.TRANSPARENT);
        back.getStyleClass().add("window-icon-outline");
        back.setStrokeWidth(1.2);
        return iconCanvas(back, front);
    }

    /**
     * Створює значок закриття з двох діагональних ліній.
     *
     * @return графічний вузол значка закриття.
     */
    static Node createCloseIcon() {
        Line first = new Line(3.0, 3.0, 13.0, 13.0);
        Line second = new Line(3.0, 13.0, 13.0, 3.0);
        first.getStyleClass().add("window-icon-stroke");
        second.getStyleClass().add("window-icon-stroke");
        first.setStrokeWidth(1.5);
        second.setStrokeWidth(1.5);
        return iconCanvas(first, second);
    }

    /**
     * Розміщує графічні елементи в області фіксованого розміру та робить полотно прозорим для миші.
     *
     * @param shapes графічні елементи, які потрібно розмістити на полотні.
     * @return контейнер однакового розміру для центрування значків.
     */
    private static Pane iconCanvas(Node... shapes) {
        Pane canvas = new Pane(shapes);
        canvas.setMinSize(16.0, 16.0);
        canvas.setPrefSize(16.0, 16.0);
        canvas.setMaxSize(16.0, 16.0);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
