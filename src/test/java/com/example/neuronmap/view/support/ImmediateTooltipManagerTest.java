package com.example.neuronmap.view.support;

import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що менеджер застосовує спільний шрифт і політику Escape до підказок дерева сцени протягом життєвого циклу. */
final class ImmediateTooltipManagerTest {

    /** Перевіряє налаштування шрифту й політики подій для підказок, створених до реєстрації менеджера. */
    @Test
    void configuresTooltipsAlreadyPresentInScene() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = buttonWithTooltip("Кнопка меню");
            Scene scene = new Scene(new StackPane(button));
            ImmediateTooltipManager manager = ImmediateTooltipManager.install(scene);

            assertEquals(14.0, button.getTooltip().getFont().getSize(), 0.001);
            assertFalse(button.getTooltip().getConsumeAutoHidingEvents());
            manager.dispose();
        });
    }

    /** Перевіряє налаштування нових рядків і кнопок, навіть коли показ підказок призупинено меню. */
    @Test
    void configuresControlsAddedWhileManagerIsPaused() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            ImmediateTooltipManager manager = ImmediateTooltipManager.install(new Scene(root));
            manager.pause();
            Button button = buttonWithTooltip("Кнопка збереженого проєкту");

            root.getChildren().add(button);

            assertEquals(14.0, button.getTooltip().getFont().getSize(), 0.001);
            assertFalse(button.getTooltip().getConsumeAutoHidingEvents());
            manager.dispose();
        });
    }

    /** Перевіряє негайне налаштування шрифту й політики для підказки, призначеної відстежуваному елементу пізніше. */
    @Test
    void configuresTooltipAssignedAfterControlWasAdded() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button("Кнопка");
            StackPane root = new StackPane(button);
            ImmediateTooltipManager manager = ImmediateTooltipManager.install(new Scene(root));
            Tooltip tooltip = new Tooltip("Підказка після створення сцени");

            button.setTooltip(tooltip);

            assertEquals(14.0, tooltip.getFont().getSize(), 0.001);
            assertFalse(tooltip.getConsumeAutoHidingEvents());
            manager.dispose();
        });
    }

    /** Перевіряє перенесення спостереження на новий кореневий вузол сцени. */
    @Test
    void tracksReplacementSceneRoot() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Scene scene = new Scene(new StackPane());
            ImmediateTooltipManager manager = ImmediateTooltipManager.install(scene);
            Button button = buttonWithTooltip("Кнопка нового кореня");

            scene.setRoot(new StackPane(button));

            assertEquals(14.0, button.getTooltip().getFont().getSize(), 0.001);
            assertFalse(button.getTooltip().getConsumeAutoHidingEvents());
            manager.dispose();
        });
    }

    /** Перевіряє, що dispose від'єднує слухачі та не налаштовує нові вузли після завершення менеджера. */
    @Test
    void disposeStopsTrackingNewControls() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            ImmediateTooltipManager manager = ImmediateTooltipManager.install(new Scene(root));
            manager.dispose();
            Button button = buttonWithTooltip("Після завершення менеджера");

            root.getChildren().add(button);

            assertTrue(button.getTooltip().getConsumeAutoHidingEvents());
        });
    }

    /** Створює кнопку з підказкою, яка навмисно має типову поведінку JavaFX до підключення менеджера. */
    private static Button buttonWithTooltip(String text) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(text));
        return button;
    }
}
