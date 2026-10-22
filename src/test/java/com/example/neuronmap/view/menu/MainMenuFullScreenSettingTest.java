package com.example.neuronmap.view.menu;

import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє розташування, попередній перегляд, відкат і збереження повноекранного параметра в меню. */
final class MainMenuFullScreenSettingTest {

    @TempDir
    private Path temporaryDirectory;

    /** Запускає JavaFX toolkit один раз перед перевірками складу налаштувань. */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Перевіряє, що перемикач повноекранного режиму розташований безпосередньо над вибором мови. */
    @Test
    void fullScreenOptionAppearsBeforeLanguageOption() throws Exception {
        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(new LocalizationService(Locale.ENGLISH), ignored -> { });
            view.setFullScreenActions(() -> false, ignored -> { });
            view.showSettingsPage();

            FullScreenSettingsView fullScreen = view.fullScreenSettingsPageForTest();
            LanguageSettingsView language = view.languageSettingsPageForTest();
            assertSame(fullScreen.getParent(), language.getParent());
            VBox settings = (VBox) fullScreen.getParent();
            assertTrue(settings.getChildren().indexOf(fullScreen) < settings.getChildren().indexOf(language));
            view.dispose();
        });
    }

    /** Перевіряє, що скасування відновлює віконний режим і не пише тимчасове значення у глобальне сховище. */
    @Test
    void discardingPreviewRestoresWindowedModeWithoutPersistence() throws Exception {
        runOnFxThread(() -> {
            GlobalSettingsFullScreenPreferenceStore preferences = preferenceStore();
            AtomicBoolean currentValue = new AtomicBoolean(false);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(view, localization);
            controller.registerFullScreenSetting(currentValue::get, currentValue::set,
                    preferences::saveFullScreenEnabled);

            controller.open();
            view.homePageForTest().settingsButtonForTest().fire();
            view.fullScreenSettingsPageForTest().toggleForTest().fire();

            assertTrue(currentValue.get());
            assertTrue(view.isSettingsDirty());
            assertTrue(view.saveSettingsButtonForTest().isVisible());
            assertFalse(preferences.loadFullScreenEnabled());

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();

            assertFalse(currentValue.get());
            assertFalse(view.fullScreenSettingsPageForTest().toggleForTest().isSelected());
            assertFalse(view.isSettingsDirty());
            assertFalse(preferences.loadFullScreenEnabled());
            controller.dispose();
        });
    }

    /** Перевіряє, що підтвердження налаштування зберігає вибір через єдиний глобальний файл властивостей. */
    @Test
    void savingPreviewPersistsTheSelectedValue() throws Exception {
        runOnFxThread(() -> {
            GlobalSettingsFullScreenPreferenceStore preferences = preferenceStore();
            AtomicBoolean currentValue = new AtomicBoolean(false);
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(view, localization);
            controller.registerFullScreenSetting(currentValue::get, currentValue::set,
                    preferences::saveFullScreenEnabled);

            controller.open();
            view.homePageForTest().settingsButtonForTest().fire();
            view.fullScreenSettingsPageForTest().toggleForTest().fire();
            view.saveSettingsButtonForTest().fire();

            assertTrue(currentValue.get());
            assertTrue(preferences.loadFullScreenEnabled());
            assertFalse(view.isSettingsDirty());
            assertFalse(view.saveSettingsButtonForTest().isVisible());
            controller.dispose();
        });
    }

    /** Створює тестовий контролер меню з безпечними діями, що не впливають на файлові проєкти. */
    private MainMenuController createController(MainMenuView view, LocalizationService localization) {
        return new MainMenuController(
                view,
                localization,
                List::of,
                () -> { },
                ignored -> { },
                (project, name) -> project,
                ignored -> { },
                () -> { },
                () -> { },
                () -> { });
    }

    /** Створює сховище параметра у тимчасовому глобальному файлі тесту. */
    private GlobalSettingsFullScreenPreferenceStore preferenceStore() {
        return new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(temporaryDirectory.resolve("global.properties")));
    }

    /** Виконує дію в JavaFX Application Thread і передає винятки потоку JUnit. */
    private static void runOnFxThread(Runnable action) throws InterruptedException {
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
