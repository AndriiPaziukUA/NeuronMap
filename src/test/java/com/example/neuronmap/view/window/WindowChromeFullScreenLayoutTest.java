package com.example.neuronmap.view.window;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє логіку компонування заголовка без створення або показу справжнього вікна. */
final class WindowChromeFullScreenLayoutTest {

    /** Перевіряє приховування заглушки заголовка у fullscreen та відновлення у звичайному режимі. */
    @Test
    void hidesAndRestoresEntireHeaderBarWithoutOpeningWindow() {
        Region headerStub = new Region();

        WindowChromeView.updateHeaderVisibility(headerStub, true);
        assertFalse(headerStub.isVisible(), "У повноекранному режимі смуга заголовка має зникати повністю");
        assertFalse(headerStub.isManaged(), "Прихований заголовок не повинен резервувати висоту");

        WindowChromeView.updateHeaderVisibility(headerStub, false);
        assertTrue(headerStub.isVisible(), "Після виходу з fullscreen заголовок має знову відображатися");
        assertTrue(headerStub.isManaged(), "Після виходу з fullscreen заголовок має повернутися до компонування");
    }
}
