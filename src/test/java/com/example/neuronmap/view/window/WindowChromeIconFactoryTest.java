package com.example.neuronmap.view.window;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє геометрію та властивості векторних значків, незалежно від життєвого циклу вікна. */
final class WindowChromeIconFactoryTest {

    /**
     * Перевіряє фіксований розмір полотна, прозорість для миші та геометрію значка згортання.
     */
    @Test
    void createsMinimizeIconOnFixedMouseTransparentCanvas() {
        Pane canvas = assertInstanceOf(Pane.class, WindowChromeIconFactory.createMinimizeIcon());
        assertCanvasProperties(canvas);
        assertEquals(1, canvas.getChildren().size());

        Line line = assertInstanceOf(Line.class, canvas.getChildren().getFirst());
        assertEquals(3.0, line.getStartX());
        assertEquals(8.0, line.getStartY());
        assertEquals(13.0, line.getEndX());
        assertEquals(8.0, line.getEndY());
        assertTrue(line.getStyleClass().contains("window-icon-stroke"));
    }

    /**
     * Перевіряє структуру значків максимізації та відновлення з однієї передньої рамки
     * й двох ліній задньої рамки для відновлення вікна.
     */
    @Test
    void distinguishesMaximizeAndRestoreIcons() {
        Pane maximize = assertInstanceOf(Pane.class, WindowChromeIconFactory.createMaximizeIcon(false));
        Pane restore = assertInstanceOf(Pane.class, WindowChromeIconFactory.createMaximizeIcon(true));

        assertCanvasProperties(maximize);
        assertCanvasProperties(restore);
        assertEquals(1, maximize.getChildren().size());
        assertEquals(3, restore.getChildren().size());

        Rectangle maximizeRectangle = assertInstanceOf(Rectangle.class, maximize.getChildren().getFirst());
        Line backTop = assertInstanceOf(Line.class, restore.getChildren().getFirst());
        Line backRight = assertInstanceOf(Line.class, restore.getChildren().get(1));
        Rectangle frontRectangle = assertInstanceOf(Rectangle.class, restore.getChildren().get(2));

        assertEquals(Color.TRANSPARENT, maximizeRectangle.getFill());
        assertEquals(6.0, backTop.getStartX());
        assertEquals(2.0, backTop.getStartY());
        assertEquals(15.0, backTop.getEndX());
        assertEquals(2.0, backTop.getEndY());
        assertEquals(15.0, backRight.getStartX());
        assertEquals(2.0, backRight.getStartY());
        assertEquals(15.0, backRight.getEndX());
        assertEquals(11.0, backRight.getEndY());
        assertTrue(backTop.getStyleClass().contains("window-icon-stroke"));
        assertTrue(backRight.getStyleClass().contains("window-icon-stroke"));
        assertEquals(Color.TRANSPARENT, frontRectangle.getFill());
        assertTrue(frontRectangle.getStyleClass().contains("window-icon-outline"));
    }

    /**
     * Перевіряє дві діагоналі значка закриття та їхній спільний стиль.
     */
    @Test
    void createsCloseIconFromTwoDiagonalLines() {
        Pane canvas = assertInstanceOf(Pane.class, WindowChromeIconFactory.createCloseIcon());
        assertCanvasProperties(canvas);
        assertEquals(2, canvas.getChildren().size());

        Line first = assertInstanceOf(Line.class, canvas.getChildren().getFirst());
        Line second = assertInstanceOf(Line.class, canvas.getChildren().get(1));
        assertEquals(3.0, first.getStartX());
        assertEquals(3.0, first.getStartY());
        assertEquals(13.0, first.getEndX());
        assertEquals(13.0, first.getEndY());
        assertEquals(3.0, second.getStartX());
        assertEquals(13.0, second.getStartY());
        assertEquals(13.0, second.getEndX());
        assertEquals(3.0, second.getEndY());
        assertTrue(canvas.getChildren().stream()
                .map(Node::getStyleClass)
                .allMatch(styleClasses -> styleClasses.contains("window-icon-stroke")));
    }

    /**
     * Перевіряє вісім ліній значка macOS та різні координати входу й виходу з повноекранного режиму.
     */
    @Test
    void createsDifferentMacOsFullScreenIconsForBothStates() {
        Pane enter = assertInstanceOf(Pane.class, WindowChromeIconFactory.createMacOsFullScreenIcon(false));
        Pane exit = assertInstanceOf(Pane.class, WindowChromeIconFactory.createMacOsFullScreenIcon(true));

        assertCanvasProperties(enter);
        assertCanvasProperties(exit);
        assertEquals(8, enter.getChildren().size());
        assertEquals(8, exit.getChildren().size());
        assertTrue(enter.getChildren().stream().allMatch(Line.class::isInstance));
        assertTrue(exit.getChildren().stream().allMatch(Line.class::isInstance));

        Line enterTopLeft = assertInstanceOf(Line.class, enter.getChildren().getFirst());
        Line exitTopLeft = assertInstanceOf(Line.class, exit.getChildren().getFirst());
        assertEquals(2.0, enterTopLeft.getStartX());
        assertEquals(2.0, enterTopLeft.getStartY());
        assertEquals(5.0, exitTopLeft.getStartX());
        assertEquals(5.0, exitTopLeft.getStartY());
    }

    /**
     * Перевіряє незмінні розміри полотна й те, що декоративний значок не перехоплює мишу.
     *
     * @param canvas полотно створеного значка.
     */
    private static void assertCanvasProperties(Pane canvas) {
        assertEquals(16.0, canvas.getMinWidth());
        assertEquals(16.0, canvas.getMinHeight());
        assertEquals(16.0, canvas.getPrefWidth());
        assertEquals(16.0, canvas.getPrefHeight());
        assertEquals(16.0, canvas.getMaxWidth());
        assertEquals(16.0, canvas.getMaxHeight());
        assertTrue(canvas.isMouseTransparent());
    }
}
