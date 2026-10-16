package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.persistence.settings.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.settings.SettingsEditSession;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.menu.MainMenuViewTestAccess;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє навігацію меню, керування локалізацією та спільну сесію попереднього перегляду налаштувань. */
final class MainMenuControllerTest {

    @TempDir
    Path tempDirectory;

    /** Запускає JavaFX toolkit для тестів клавіатурних подій. */
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

    /** Перевіряє, що Escape повертає зі сторінок проєктів і налаштувань, а потім закриває меню. */
    @Test
    void escapeNavigatesBackFromSubpagesAndClosesMainPage() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            Scene scene = new Scene(view, 900.0, 600.0);
            controller.install(scene);
            controller.open();

            view.showLoadPage(List.of());
            fireEscape(scene);
            assertTrue(controller.isOpen());
            assertFalse(view.isLoadPage());
            assertFalse(view.isSettingsPage());

            view.showSettingsPage();
            fireEscape(scene);
            assertTrue(controller.isOpen());
            assertFalse(view.isSettingsPage());

            fireEscape(scene);
            assertFalse(controller.isOpen());
            controller.dispose(scene);
        });
    }

    /** Перевіряє, що скасування незбереженої мови відновлює початкову локаль перед переходом назад. */
    @Test
    void escapeDiscardsDirtySettingsAndContinuesBackNavigation() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            Scene scene = new Scene(view, 900.0, 600.0);
            controller.install(scene);
            controller.open();
            view.showSettingsPage();
            chooseLanguage(view, SupportedLanguage.UKRAINIAN);

            assertTrue(view.hasUnsavedChanges());
            fireEscape(scene);
            assertTrue(view.isConfirmingUnsavedChanges());

            fireEscape(scene);
            assertFalse(view.isConfirmingUnsavedChanges());
            assertTrue(controller.isOpen());
            assertFalse(view.isSettingsPage());
            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertFalse(view.hasUnsavedChanges());

            fireEscape(scene);
            assertFalse(controller.isOpen());
            controller.dispose(scene);
        });
    }

    /** Перевіряє, що підтвердження мови контролером зберігає вибір у сховищі налаштувань. */
    @Test
    void confirmingLanguagePersistsSelectionThroughLocalizationService() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(tempDirectory.resolve("settings.properties"));
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsLanguagePreferenceStore(store)
        );
        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            controller.open();
            view.showSettingsPage();

            chooseLanguage(view, SupportedLanguage.UKRAINIAN);
            assertTrue(view.hasUnsavedChanges());
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());

            MainMenuViewTestAccess.fireSaveSettings(view);

            assertEquals("uk", store.load("language"));
            assertFalse(view.hasUnsavedChanges());
            assertFalse(MainMenuViewTestAccess.isSaveSettingsVisible(view));
            controller.dispose(null);
        });
    }

    /** Перевіряє, що відповідь «Ні» після повернення скасовує всі прев'ю та не записує їх у постійні сховища. */
    @Test
    void backThenNoRollsBackEveryPreviewWithoutPersisting() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(
                tempDirectory.resolve("settings-cancel.properties")
        );
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsLanguagePreferenceStore(store)
        );
        AtomicReference<String> activeTheme = new AtomicReference<>("light");
        AtomicReference<String> persistedTheme = new AtomicReference<>("light");

        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            SettingsEditSession.Setting<String> themeSetting = controller.registerSetting(
                    activeTheme::get,
                    activeTheme::set,
                    persistedTheme::set
            );
            controller.open();
            view.showSettingsPage();

            controller.previewSetting(themeSetting, "dark");
            assertTrue(MainMenuViewTestAccess.isSaveSettingsVisible(view));
            controller.previewSetting(themeSetting, "light");
            assertFalse(MainMenuViewTestAccess.isSaveSettingsVisible(view));
            controller.previewSetting(themeSetting, "dark");
            assertTrue(MainMenuViewTestAccess.isSaveSettingsVisible(view));
            chooseLanguage(view, SupportedLanguage.UKRAINIAN);

            assertEquals("dark", activeTheme.get());
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertNull(store.load("language"));
            assertEquals("light", persistedTheme.get());
            assertTrue(view.hasUnsavedChanges());

            MainMenuViewTestAccess.fireBack(view);
            assertTrue(view.isConfirmingUnsavedChanges());
            assertTrue(view.isSettingsPage());

            view.discardAndContinueUnsavedChanges();

            assertFalse(view.isConfirmingUnsavedChanges());
            assertFalse(view.isSettingsPage());
            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertEquals("light", activeTheme.get());
            assertEquals("light", persistedTheme.get());
            assertNull(store.load("language"));
            assertFalse(view.hasUnsavedChanges());
            controller.dispose(null);
        });
    }

    /** Перевіряє, що підтвердження зберігає всі змінені налаштування через єдину сесію. */
    @Test
    void confirmingSettingsCommitsEveryChangedValue() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(
                tempDirectory.resolve("settings-commit.properties")
        );
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsLanguagePreferenceStore(store)
        );
        AtomicReference<String> activeTheme = new AtomicReference<>("light");
        AtomicReference<String> persistedTheme = new AtomicReference<>("light");

        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = controllerFor(view, localization);
            SettingsEditSession.Setting<String> themeSetting = controller.registerSetting(
                    activeTheme::get,
                    activeTheme::set,
                    persistedTheme::set
            );
            controller.open();
            view.showSettingsPage();

            controller.previewSetting(themeSetting, "dark");
            chooseLanguage(view, SupportedLanguage.UKRAINIAN);
            MainMenuViewTestAccess.fireSaveSettings(view);

            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertEquals("uk", store.load("language"));
            assertEquals("dark", activeTheme.get());
            assertEquals("dark", persistedTheme.get());
            assertFalse(view.hasUnsavedChanges());
            assertFalse(MainMenuViewTestAccess.isSaveSettingsVisible(view));
            controller.dispose(null);
        });
    }

    /** Створює контролер з порожніми діями застосунку для ізольованих тестів меню. */
    private static MainMenuController controllerFor(
            MainMenuView view,
            LocalizationService localization
    ) {
        return new MainMenuController(
                view,
                localization,
                () -> List.of(),
                () -> { },
                ignored -> { },
                (project, name) -> project,
                ignored -> { },
                () -> { },
                () -> { },
                () -> { }
        );
    }

    /** Вибирає мову так, як це відбулося б під час дії користувача у ComboBox. */
    private static void chooseLanguage(MainMenuView view, SupportedLanguage language) {
        MainMenuViewTestAccess.chooseLanguage(view, language);
    }

    /** Передає подію Escape через фільтри подій сцени. */
    private static void fireEscape(Scene scene) {
        Event.fireEvent(scene, new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                KeyCode.ESCAPE,
                false,
                false,
                false,
                false
        ));
    }

    /** Виконує перевірки в потоці JavaFX і передає помилки до потоку JUnit. */
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Тест JavaFX завершився помилкою", failure[0]);
        }
    }
}
