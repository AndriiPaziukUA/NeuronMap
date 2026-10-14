package com.example.neuronmap.view.menu;

import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.persistence.settings.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє розділення сторінок головного меню та керування зміною мови контролером. */
final class MainMenuPagesCompositionTest {

    @TempDir
    Path temporaryDirectory;

    /** Запускає JavaFX toolkit один раз для тестів компонентів меню. */
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

    /** Перевіряє, що контейнер послідовно показує кожну спеціалізовану сторінку. */
    @Test
    void containerSwitchesBetweenDedicatedPageViews() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            MainMenuView view = new MainMenuView(localization, ignored -> { });

            assertNotNull(view.homePageForTest().getParent());
            assertEquals(List.of(
                    "Продовжити", "Новий проєкт", "Завантажити", "Налаштування", "Вихід"
            ), view.mainButtonTextsForTest());

            view.showLoadPage(List.of());
            assertTrue(view.isLoadPage());
            assertNotNull(view.projectListPageForTest().getParent());
            assertNotNull(view.projectScrollPaneForTest());

            view.showSettingsPage();
            assertTrue(view.isSettingsPage());
            assertNotNull(view.languageSettingsPageForTest().getParent());
            view.dispose();
        });
    }

    /** Перевіряє, що контролер обробляє попередній перегляд, збереження та скасування мови. */
    @Test
    void controllerOwnsLanguagePreviewPersistenceAndDiscard() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("settings.properties"));
        LocalizationService localization = new LocalizationService(
                new GlobalSettingsLanguagePreferenceStore(store));
        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = new MainMenuController(
                    view,
                    localization,
                    List::of,
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            controller.open();
            view.homePageForTest().settingsButtonForTest().fire();

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.UKRAINIAN);
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertTrue(view.isSettingsDirty());
            assertTrue(view.saveSettingsButtonForTest().isVisible());

            view.saveSettingsButtonForTest().fire();
            assertFalse(view.isSettingsDirty());
            assertEquals("uk", store.load("language"));
            List<String> savedLanguageButtonTexts = view.mainButtonTextsForTest();

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.RUSSIAN);
            assertTrue(view.isSettingsDirty());
            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();

            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertEquals(savedLanguageButtonTexts, view.mainButtonTextsForTest());
            assertEquals("uk", store.load("language"));
            assertEquals(SupportedLanguage.UKRAINIAN,
                    new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store)).language());
            assertFalse(view.isSettingsPage());
            assertFalse(view.hasUnsavedChanges());
            controller.dispose(null);
        });
    }

    /**
     * Перевіряє точний сценарій регресії: українська мова, попередній перегляд англійської,
     * «Назад» і натискання «Ні» мають негайно відновити українські написи без зміни сховища.
     */
    @Test
    void discardingEnglishPreviewRestoresUkrainianThroughTheActualNoButton() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("ukrainian-settings.properties"));
        store.save("language", "uk");
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));
        runOnFxThread(() -> {
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = new MainMenuController(
                    view,
                    localization,
                    List::of,
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            controller.open();
            List<String> originalTexts = view.mainButtonTextsForTest();
            view.homePageForTest().settingsButtonForTest().fire();

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.ENGLISH);
            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertTrue(view.isSettingsDirty());

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();

            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertEquals(SupportedLanguage.UKRAINIAN, localization.confirmedLanguage());
            assertFalse(localization.hasUnconfirmedLanguagePreview());
            assertEquals(originalTexts, view.mainButtonTextsForTest());
            assertEquals("uk", store.load("language"));
            assertFalse(view.isSettingsPage());
            assertFalse(view.hasUnsavedChanges());

            // Повторний вхід має починатися з підтвердженої мови, а не з відхиленого попереднього перегляду.
            view.homePageForTest().settingsButtonForTest().fire();
            assertEquals(SupportedLanguage.UKRAINIAN, view.languageSelectorForTest().getValue());
            assertFalse(view.saveSettingsButtonForTest().isVisible());
            controller.dispose(null);
        });
    }

    /**
     * Перевіряє скасування першого попереднього перегляду мови без попереднього збереження налаштувань.
     * Мова інтерфейсу має відновитися, а сховище не повинно отримати тимчасове значення.
     */
    @Test
    void discardingFirstLanguagePreviewRestoresDefaultWithoutPersistingIt() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("fresh-settings.properties"));
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));
        runOnFxThread(() -> {
            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertNull(store.load("language"));

            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = new MainMenuController(
                    view,
                    localization,
                    List::of,
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            controller.open();
            List<String> originalHomeButtonTexts = view.mainButtonTextsForTest();
            view.homePageForTest().settingsButtonForTest().fire();

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.UKRAINIAN);
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertTrue(view.hasUnsavedChanges());

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();

            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertEquals(SupportedLanguage.ENGLISH, localization.confirmedLanguage());
            assertFalse(localization.hasUnconfirmedLanguagePreview());
            assertEquals(originalHomeButtonTexts, view.mainButtonTextsForTest());
            assertFalse(view.isSettingsPage());
            assertFalse(view.hasUnsavedChanges());
            assertNull(store.load("language"));
            assertEquals(SupportedLanguage.ENGLISH,
                    new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store)).language());
            controller.dispose(null);
        });
    }

    /**
     * Перевіряє повторні сеанси налаштувань: скасування кожного попереднього перегляду
     * повертає підтверджену мову, оновлює тексти й не записує тимчасовий вибір у сховище.
     */
    @Test
    void repeatedDiscardSessionsRestoreConfirmedLanguageWithoutPersistingPreview() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(temporaryDirectory.resolve("repeated-settings.properties"));
        LocalizationService localization = new LocalizationService(new GlobalSettingsLanguagePreferenceStore(store));
        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = new MainMenuController(
                    view,
                    localization,
                    List::of,
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { }
            );
            controller.open();
            List<String> originalTexts = view.mainButtonTextsForTest();

            for (SupportedLanguage preview : List.of(SupportedLanguage.UKRAINIAN, SupportedLanguage.RUSSIAN)) {
                view.homePageForTest().settingsButtonForTest().fire();
                chooseLanguage(view.languageSelectorForTest(), preview);
                assertEquals(preview, localization.language());
                assertTrue(view.isSettingsDirty());

                view.backButtonForTest().fire();
                assertTrue(view.isConfirmingUnsavedChanges());
                view.discardChangesButtonForTest().fire();

                assertEquals(SupportedLanguage.ENGLISH, localization.language());
                assertEquals(SupportedLanguage.ENGLISH, localization.confirmedLanguage());
                assertFalse(localization.hasUnconfirmedLanguagePreview());
                assertEquals(originalTexts, view.mainButtonTextsForTest());
                assertFalse(view.isSettingsPage());
                assertFalse(view.hasUnsavedChanges());
                assertNull(store.load("language"));
            }

            controller.dispose(null);
        });
    }

    /**
     * Перевіряє, що програмне оновлення ComboBox після відкату не генерує повторний вибір
     * і не повертає відхилену англійську мову через застаріле значення елемента керування.
     */
    @Test
    void refreshingLanguageSelectorCannotReapplyRejectedLanguage() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            LanguageSettingsView view = new LanguageSettingsView(localization);
            java.util.concurrent.atomic.AtomicInteger previewCount = new java.util.concurrent.atomic.AtomicInteger();
            view.setLanguageSelectedAction(language -> {
                previewCount.incrementAndGet();
                localization.previewLanguage(language);
            });

            // Імітуємо застаріле значення ComboBox перед оновленням подання після відкату.
            view.languageSelectorForTest().setValue(SupportedLanguage.ENGLISH);
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            view.refreshTexts();

            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertEquals(SupportedLanguage.UKRAINIAN, view.languageSelectorForTest().getValue());
            assertEquals(0, previewCount.get());
        });
    }

    /** Встановлює вибір мови й викликає обробник так, як це робить комбінований список JavaFX. */
    private static void chooseLanguage(
            ComboBox<SupportedLanguage> selector,
            SupportedLanguage language
    ) {
        selector.setValue(language);
        selector.getOnAction().handle(new ActionEvent(selector, null));
    }

    /** Виконує тестову дію в потоці JavaFX і повертає її помилку до потоку JUnit. */
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
            throw new AssertionError("Тест меню завершився помилкою", failure[0]);
        }
    }
}
