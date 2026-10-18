package com.example.neuronmap.view.neuron;

import com.example.neuronmap.controller.EditorKeyboardController;
import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.menu.MainMenuView;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє ізоляцію гарячих клавіш, Escape і життєвий цикл локалізації модальної накладки. */
final class NeuronSettingsModalKeyboardTest {

    /** Перевіряє ізоляцію гарячих клавіш і локальне скасування Escape з поля введення. */
    @Test
    void modalSettingsIsolateEditorShortcutsAndHandleEscapeLocally() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            TextField backgroundField = new TextField();
            root.getChildren().add(backgroundField);
            Scene scene = new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            AtomicInteger undoCalls = new AtomicInteger();
            AtomicInteger redoCalls = new AtomicInteger();
            AtomicInteger deleteCalls = new AtomicInteger();
            AtomicInteger cancelCalls = new AtomicInteger();
            AtomicInteger pauseCalls = new AtomicInteger();
            EditorKeyboardController keyboard = new EditorKeyboardController(
                    () -> { undoCalls.incrementAndGet(); return true; },
                    () -> { redoCalls.incrementAndGet(); return true; },
                    () -> { deleteCalls.incrementAndGet(); return true; },
                    cancelCalls::incrementAndGet
            );
            keyboard.install(scene);
            MainMenuView menuView = new MainMenuView(localization, ignored -> { });
            MainMenuController menuController = new MainMenuController(
                    menuView,
                    localization,
                    List::of,
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { },
                    pauseCalls::incrementAndGet,
                    () -> { }
            );
            menuController.install(scene);
            view.showIn(root, neuron("keyboard", 4, 6), ignored -> { });

            // Ціль поза накладкою перевіряє, що scene filter зберігає модальність навіть після втрати фокуса.
            fireKey(root, KeyCode.Z, true);
            fireKey(root, KeyCode.Y, true);
            fireKey(root, KeyCode.DELETE, false);

            assertEquals(0, undoCalls.get());
            assertEquals(0, redoCalls.get());
            assertEquals(0, deleteCalls.get());
            assertEquals(0, cancelCalls.get());
            Event.fireEvent(backgroundField, new KeyEvent(
                    KeyEvent.KEY_TYPED, "x", "x", KeyCode.UNDEFINED,
                    false, false, false, false));
            assertEquals("", backgroundField.getText(),
                    "Модальна накладка має блокувати введення в текстові поля поза нею");
            assertEquals(0, pauseCalls.get(), "Escape не повинен відкривати головне меню поверх накладки");
            assertFalse(menuView.isMenuVisible());
            assertTrue(view.blocksEditorShortcuts());

            fireKey(root, KeyCode.ESCAPE, false);
            assertFalse(view.isVisible());
            assertFalse(menuView.isMenuVisible(), "Escape поза накладкою не повинен відкривати головне меню");
            assertEquals(0, pauseCalls.get(), "Escape не повинен запускати паузу головного меню");

            fireKey(root, KeyCode.Z, true);
            assertEquals(1, undoCalls.get(), "Після закриття накладки фільтр сцени має бути знято");
            Event.fireEvent(backgroundField, new KeyEvent(
                    KeyEvent.KEY_TYPED, "y", "y", KeyCode.UNDEFINED,
                    false, false, false, false));
            assertEquals("y", backgroundField.getText(),
                    "Після закриття накладки введення поза нею має працювати знову");

            view.showIn(root, neuron("keyboard-again", 4, 6), ignored -> { });
            fireKey(root, KeyCode.Z, true);
            assertEquals(1, undoCalls.get(), "Повторно відкрита накладка має знову блокувати гарячі клавіші");
            Event.fireEvent(backgroundField, new KeyEvent(
                    KeyEvent.KEY_TYPED, "z", "z", KeyCode.UNDEFINED,
                    false, false, false, false));
            assertEquals("y", backgroundField.getText(),
                    "Повторно відкрита накладка має знову блокувати введення поза нею");
            fireKey(view.signalFieldForTest(), KeyCode.ESCAPE, false);

            assertFalse(view.isVisible());
            assertFalse(root.getChildren().contains(view));
            assertFalse(view.blocksEditorShortcuts());
            assertFalse(menuView.isMenuVisible(), "Escape з поля введення не повинен відкривати головне меню");
            assertEquals(0, pauseCalls.get(), "Escape не повинен запускати паузу головного меню");
            assertEquals(0, cancelCalls.get(), "Escape не повинен запускати скасування взаємодії редактора");
            menuController.dispose(scene);
            keyboard.dispose();
            view.dispose();
        });
    }

    /** Перевіряє, що клацання по затемненню закриває накладку без збереження змін. */
    @Test
    void clickingBackdropCancelsSettingsWithoutSaving() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            AtomicInteger saveCalls = new AtomicInteger();
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            view.showIn(root, neuron("backdrop", 4, 6), ignored -> saveCalls.incrementAndGet());
            Node backdrop = view.backdropForTest();
            assertNotNull(backdrop, "Затемнений фон має належати власному поданню");

            Event.fireEvent(backdrop, new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    10.0, 10.0, 10.0, 10.0,
                    MouseButton.PRIMARY, 1,
                    false, false, false, false,
                    true, false, false,
                    false, false, true,
                    null
            ));

            assertFalse(view.isVisible());
            assertFalse(root.getChildren().contains(view));
            assertEquals(0, saveCalls.get(), "Клацання по затемненню має скасувати введення без збереження");
            assertFalse(view.blocksEditorShortcuts());
            view.dispose();
        });
    }

    /** Перевіряє локалізовану кнопку скасування, підказки числових полів та відписку після закриття. */
    @Test
    void usesLocalizedCancelAndNumericPromptsAndStopsListeningWhenHidden() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron("localization", 4, 6), ignored -> { });

            assertEquals("Neuron Settings", view.titleForTest().getText());
            assertEquals("Cancel", view.cancelButtonForTest().getText());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.signalPromptForTest());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.thresholdPromptForTest());

            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals("Налаштування нейрона", view.titleForTest().getText());
            assertEquals("Скасувати", view.cancelButtonForTest().getText());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.signalPromptForTest());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.thresholdPromptForTest());
            assertEquals(localization.text("neuron.settings.signal_strength"),
                    view.settingLabelTextForTest("neuron.settings.signal_strength"));

            localization.previewLanguage(SupportedLanguage.RUSSIAN);
            String russianLabel = view.settingLabelTextForTest("neuron.settings.signal_strength");
            String russianCancel = view.cancelButtonForTest().getText();
            assertEquals("Настройки нейрона", view.titleForTest().getText());
            assertEquals("Отмена", russianCancel);
            assertEquals(localization.text("neuron.settings.positive_integer"), view.signalPromptForTest());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.thresholdPromptForTest());
            view.hide();

            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals(russianLabel, view.settingLabelTextForTest("neuron.settings.signal_strength"),
                    "Приховане подання не повинно утримувати слухача локалізації");
            assertEquals(russianCancel, view.cancelButtonForTest().getText(),
                    "Приховане подання не повинно перемальовуватися після зміни локалі");
            view.dispose();
            assertThrows(IllegalStateException.class,
                    () -> view.showIn(root, neuron("disposed", 4, 6), ignored -> { }),
                    "Остаточно звільнене подання не повинно відкриватися повторно");
        });
    }

    /** Створює нейрон для UI-перевірок без залежності від стану редактора. */
    private static Neuron neuron(String id, int signal, int threshold) {
        return new Neuron(id, NeuronType.EXCITATORY, 0, signal, threshold);
    }

    /** Надсилає клавіатурну подію через стандартний механізм маршрутизації JavaFX. */
    private static void fireKey(Node target, KeyCode code, boolean controlDown) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, controlDown, false, false));
    }

}
