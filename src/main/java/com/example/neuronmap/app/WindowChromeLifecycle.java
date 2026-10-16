package com.example.neuronmap.app;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.window.WindowChromeView;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Володіє єдиним життєвим циклом оформлення головного вікна на базі API JavaFX.
 * Візуальні платформні відмінності делеговано {@link WindowChromeView} і його платформному опису.
 */
final class WindowChromeLifecycle {

    private static final Logger LOGGER = Logger.getLogger(WindowChromeLifecycle.class.getName());

    private final Stage stage;
    private final LocalizationService localization;
    private final BooleanSupplier extendedWindowSupport;
    private final javafx.event.EventHandler<WindowEvent> hiddenHandler = event -> dispose();

    private WindowChromeView windowChromeView;
    private boolean configured;
    private boolean hiddenHandlerRegistered;

    /**
     * Створює власника оформлення вікна з перевіркою підтримки JavaFX.
     *
     * @param stage системне JavaFX-вікно.
     * @param localization спільна служба локалізації застосунку.
     * @param extendedWindowSupport перевірка підтримки розширеного системного заголовка.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    WindowChromeLifecycle(
            Stage stage,
            LocalizationService localization,
            BooleanSupplier extendedWindowSupport
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.extendedWindowSupport = Objects.requireNonNull(extendedWindowSupport, "extendedWindowSupport");
    }

    /**
     * Налаштовує єдиний механізм оформлення до показу Stage.
     * Якщо JavaFX не підтримує розширений заголовок, метод завершується явною помилкою без fallback.
     *
     * @param scene сцена редактора, корінь якої отримає заголовок.
     * @throws NullPointerException якщо сцену не задано.
     * @throws IllegalStateException якщо налаштування вже відбувалося.
     * @throws UnsupportedOperationException якщо toolkit не підтримує {@code EXTENDED_WINDOW}.
     */
    void configure(Scene scene) {
        if (configured) {
            throw new IllegalStateException("window chrome lifecycle is already configured");
        }
        Objects.requireNonNull(scene, "scene");
        if (!extendedWindowSupport.getAsBoolean()) {
            throw new UnsupportedOperationException(
                    "NeuronMap requires JavaFX support for ConditionalFeature.EXTENDED_WINDOW");
        }

        WindowChromeView view = null;
        try {
            stage.initStyle(StageStyle.EXTENDED);
            view = new WindowChromeView(stage, localization);
            windowChromeView = view;
            view.installInto(scene);
            view.installExtendedHeaderBar();
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
            hiddenHandlerRegistered = true;
            configured = true;
        } catch (RuntimeException | Error configurationFailure) {
            if (view != null) {
                try {
                    view.dispose();
                    windowChromeView = null;
                } catch (RuntimeException | Error cleanupFailure) {
                    addSuppressedFailure(configurationFailure, cleanupFailure);
                }
            }
            throw configurationFailure;
        }
    }

    /**
     * Звільняє слухачі локалі, стану вікна та обробник приховування.
     * Посилання на подання зберігається до успішного очищення, щоб дозволити повторну спробу.
     */
    void dispose() {
        if (hiddenHandlerRegistered) {
            try {
                stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hiddenHandler);
                hiddenHandlerRegistered = false;
            } catch (RuntimeException | Error cleanupFailure) {
                LOGGER.log(Level.WARNING, "Не вдалося зняти обробник приховування головного вікна", cleanupFailure);
            }
        }

        WindowChromeView viewToDispose = windowChromeView;
        if (viewToDispose == null) {
            return;
        }
        try {
            viewToDispose.dispose();
            windowChromeView = null;
        } catch (RuntimeException | Error cleanupFailure) {
            LOGGER.log(Level.WARNING, "Не вдалося повністю очистити ресурси оформлення головного вікна",
                    cleanupFailure);
        }
    }

    /**
     * Додає помилку очищення до першопричини, не замінюючи помилку конфігурації.
     *
     * @param primaryFailure первинна помилка налаштування.
     * @param cleanupFailure помилка, що виникла під час очищення.
     */
    private static void addSuppressedFailure(Throwable primaryFailure, Throwable cleanupFailure) {
        if (primaryFailure != cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }
}
