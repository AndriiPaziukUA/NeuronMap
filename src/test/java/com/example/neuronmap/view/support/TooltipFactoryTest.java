package com.example.neuronmap.view.support;

import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє централізоване створення підказок, розмір шрифту, часові параметри та передавання Escape вікну-власнику. */
final class TooltipFactoryTest {

    /** Перевіряє текст, збільшений спільний шрифт, часові параметри та непоглинання Escape новою підказкою. */
    @Test
    void createUsesSharedFontSizeAndTimingWithoutConsumingAutoHideEvents() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Tooltip tooltip = TooltipFactory.create("Підказка");

            assertEquals("Підказка", tooltip.getText());
            assertEquals(14.0, tooltip.getFont().getSize(), 0.001);
            assertEquals(UiTiming.TOOLTIP_SHOW_DELAY.toMillis(), tooltip.getShowDelay().toMillis());
            assertEquals(UiTiming.TOOLTIP_VISIBLE_DURATION.toMillis(), tooltip.getShowDuration().toMillis());
            assertEquals(UiTiming.TOOLTIP_HIDE_DELAY.toMillis(), tooltip.getHideDelay().toMillis());
            assertFalse(tooltip.getConsumeAutoHidingEvents());
        });
    }

    /** Перевіряє, що відсутній текст підказки перетворюється на порожній рядок. */
    @Test
    void createNormalizesNullText() throws Exception {
        JavaFxTestSupport.runAndWait(() -> assertEquals("", TooltipFactory.create(null).getText()));
    }

    /** Перевіряє, що налаштування наявної підказки відновлює спільний шрифт, часові параметри та політику подій. */
    @Test
    void configureResetsFontSizeTimingAndAutoHideEventPolicy() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Tooltip tooltip = new Tooltip("Наявна підказка");
            tooltip.setFont(Font.font("Serif", 9.0));
            String originalFontFamily = tooltip.getFont().getFamily();
            tooltip.setShowDelay(Duration.seconds(9));
            tooltip.setShowDuration(Duration.seconds(10));
            tooltip.setHideDelay(Duration.seconds(11));
            tooltip.setConsumeAutoHidingEvents(true);

            TooltipFactory.configure(tooltip);

            assertEquals(14.0, tooltip.getFont().getSize(), 0.001);
            assertEquals(originalFontFamily, tooltip.getFont().getFamily());
            assertEquals(UiTiming.TOOLTIP_SHOW_DELAY.toMillis(), tooltip.getShowDelay().toMillis());
            assertEquals(UiTiming.TOOLTIP_VISIBLE_DURATION.toMillis(), tooltip.getShowDuration().toMillis());
            assertEquals(UiTiming.TOOLTIP_HIDE_DELAY.toMillis(), tooltip.getHideDelay().toMillis());
            assertFalse(tooltip.getConsumeAutoHidingEvents());
        });
    }

    /** Перевіряє, що встановлення підказки через фабрику одразу застосовує спільний шрифт і політику подій. */
    @Test
    void installConfiguresTooltipBeforeItIsShown() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button();
            TooltipFactory.install(button, "Підказка кнопки");

            assertEquals("Підказка кнопки", button.getTooltip().getText());
            assertEquals(14.0, button.getTooltip().getFont().getSize(), 0.001);
            assertFalse(button.getTooltip().getConsumeAutoHidingEvents());
        });
    }

    /** Перевіряє, що передача null замість елемента керування не приховується фабрикою. */
    @Test
    void installRejectsNullControl() throws Exception {
        JavaFxTestSupport.runAndWait(() -> assertThrows(
                IllegalArgumentException.class,
                () -> TooltipFactory.install(null, "Підказка")
        ));
    }
}
