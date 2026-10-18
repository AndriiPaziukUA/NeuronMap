package com.example.neuronmap.view.editor;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє контракт незалежного незмінного опису стилю координатної сітки.
 */
final class GridRendererStyleTest {

    /**
     * Переконується, що сумісний стиль має очікувані параметри лише для сітки.
     */
    @Test
    void defaultStyleContainsGridSpecificValues() {
        GridRenderer.GridStyle style = GridRenderer.GridStyle.DEFAULT;

        assertEquals(40.0, style.gridSize());
        assertEquals(500, style.maxLinesPerAxis());
        assertEquals(Color.web("#252a31"), style.color());
    }

    /**
     * Не дозволяє створити стиль із нульовою або нескінченною відстанню між лініями.
     */
    @Test
    void gridStyleRejectsInvalidSpacing() {
        assertThrows(IllegalArgumentException.class,
                () -> new GridRenderer.GridStyle(0.0, 10, Color.BLACK));
        assertThrows(IllegalArgumentException.class,
                () -> new GridRenderer.GridStyle(Double.POSITIVE_INFINITY, 10, Color.BLACK));
    }

    /**
     * Не дозволяє вимкнути обмеження кількості ліній або передати невизначений колір.
     */
    @Test
    void gridStyleRequiresLineLimitAndColor() {
        assertThrows(IllegalArgumentException.class,
                () -> new GridRenderer.GridStyle(20.0, 0, Color.BLACK));
        assertThrows(NullPointerException.class,
                () -> new GridRenderer.GridStyle(20.0, 10, null));
    }
}
