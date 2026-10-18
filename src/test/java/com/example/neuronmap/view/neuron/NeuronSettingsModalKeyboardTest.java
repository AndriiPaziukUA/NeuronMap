package com.example.neuronmap.view.neuron;

import com.example.neuronmap.controller.EditorKeyboardController;
import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.menu.MainMenuView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє ізоляцію гарячих клавіш, Escape і життєвий цикл локалізації модальної накладки. */
final class NeuronSettingsModalKeyboardTest {

    /** Запускає JavaFX toolkit перед тестуванням клавіатурних подій та подань. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє, що гарячі клавіші редактора не виконуються поверх налаштувань, а Escape закриває їх. */
    @Test
    void modalSettingsIsolateEditorShortcutsAndHandleEscapeLocally() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
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

            fireKey(view, KeyCode.Z, true);
            fireKey(view, KeyCode.Y, true);
            fireKey(view, KeyCode.DELETE, false);

            assertEquals(0, undoCalls.get());
            assertEquals(0, redoCalls.get());
            assertEquals(0, deleteCalls.get());
            assertEquals(0, cancelCalls.get());
            assertEquals(0, pauseCalls.get(), "Escape не повинен відкривати головне меню поверх накладки");
            assertFalse(menuView.isMenuVisible());
            assertTrue(view.blocksEditorShortcuts());

            fireKey(view, KeyCode.ESCAPE, false);

            assertFalse(view.isVisible());
            assertFalse(root.getChildren().contains(view));
            assertFalse(view.blocksEditorShortcuts());
            assertEquals(0, cancelCalls.get(), "Escape не повинен запускати скасування взаємодії редактора");
            menuController.dispose(scene);
            keyboard.dispose();
            view.dispose();
        });
    }

    /** Перевіряє локалізовану кнопку скасування, підказки числових полів та відписку після закриття. */
    @Test
    void usesLocalizedCancelAndNumericPromptsAndStopsListeningWhenHidden() throws Exception {
        runOnFxThread(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronSettingsView view = new NeuronSettingsView(localization);
            view.showIn(root, neuron("localization", 4, 6), ignored -> { });

            assertEquals(localization.textFrom("i18n.neuronSettingsTitle", "cancel"),
                    view.cancelButtonForTest().getText());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.signalPromptForTest());
            assertEquals(localization.text("neuron.settings.positive_integer"), view.thresholdPromptForTest());

            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            String translatedLabel = view.settingLabelTextForTest("neuron.settings.signal_strength");
            String translatedCancel = view.cancelButtonForTest().getText();
            assertEquals(localization.text("neuron.settings.signal_strength"), translatedLabel);
            assertEquals(localization.textFrom("i18n.neuronSettingsTitle", "cancel"), translatedCancel);
            view.hide();

            localization.previewLanguage(SupportedLanguage.RUSSIAN);
            assertEquals(translatedLabel, view.settingLabelTextForTest("neuron.settings.signal_strength"),
                    "Приховане подання не повинно утримувати слухача локалізації");
            assertEquals(translatedCancel, view.cancelButtonForTest().getText(),
                    "Приховане подання не повинно перемальовуватися після зміни локалі");
            view.dispose();
        });
    }

    /** Створює нейрон для UI-перевірок без залежності від стану редактора. */
    private static Neuron neuron(String id, int signal, int threshold) {
        return new Neuron(id, NeuronType.EXCITATORY, 0, signal, threshold);
    }

    /** Надсилає клавіатурну подію через стандартний механізм маршрутизації JavaFX. */
    private static void fireKey(javafx.scene.Node target, KeyCode code, boolean controlDown) {
        Event.fireEvent(target, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, controlDown, false, false));
    }

    /** Виконує UI-перевірку в JavaFX Application Thread і передає помилку до JUnit. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action має завершитися");
        if (failure[0] != null) {
            throw new AssertionError("Перевірка модальної клавіатурної поведінки завершилася помилкою", failure[0]);
        }
    }
}
