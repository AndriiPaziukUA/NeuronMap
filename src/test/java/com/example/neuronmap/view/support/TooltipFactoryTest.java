package com.example.neuronmap.view.support;

import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє централізоване створення підказок, їхні часові параметри та передавання Escape вікну-власнику. */
final class TooltipFactoryTest {

    /** Перевіряє текст, спільні часові параметри та непоглинання Escape новоствореною підказкою. */
    @Test
    void createUsesSharedTimingAndDoesNotConsumeAutoHideEvents() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Tooltip tooltip = TooltipFactory.create("Підказка");

            assertEquals("Підказка", tooltip.getText());
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

    /** Перевіряє застосування спільної політики до вже створеної підказки з довільними параметрами. */
    @Test
    void configureResetsTimingAndAutoHideEventPolicy() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Tooltip tooltip = new Tooltip("Наявна підказка");
            tooltip.setShowDelay(Duration.seconds(9));
            tooltip.setShowDuration(Duration.seconds(10));
            tooltip.setHideDelay(Duration.seconds(11));
            tooltip.setConsumeAutoHidingEvents(true);

            TooltipFactory.configure(tooltip);

            assertEquals(UiTiming.TOOLTIP_SHOW_DELAY.toMillis(), tooltip.getShowDelay().toMillis());
            assertEquals(UiTiming.TOOLTIP_VISIBLE_DURATION.toMillis(), tooltip.getShowDuration().toMillis());
            assertEquals(UiTiming.TOOLTIP_HIDE_DELAY.toMillis(), tooltip.getHideDelay().toMillis());
            assertFalse(tooltip.getConsumeAutoHidingEvents());
        });
    }

    /** Перевіряє, що встановлення підказки через фабрику одразу застосовує спільну клавіатурну політику. */
    @Test
    void installConfiguresTooltipBeforeItIsShown() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button();
            TooltipFactory.install(button, "Підказка кнопки");

            assertEquals("Підказка кнопки", button.getTooltip().getText());
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
