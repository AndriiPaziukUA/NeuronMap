package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє однакове оформлення й розмір кнопок підтвердження незбережених змін. */
final class MainMenuButtonConsistencyTest {

    /** Запускає JavaFX toolkit перед перевіркою компонентів меню.
     *
     * @throws Exception якщо JavaFX toolkit не запускається вчасно.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit має запуститися");
    }

    /** Перевіряє, що «Так» і «Ні» використовують один незалежний стиль і розміри більшої кнопки. */
    @Test
    void confirmationButtonsShareStyleTypographyAndContentBasedDimensions() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView menu = new MainMenuView(localization, ignored -> { });
            menu.setActions(new MainMenuView.Actions(
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { },
                    ignored -> { }, (project, name) -> project, ignored -> { }, () -> { }
            ));
            StackPane root = new StackPane(menu);
            Scene scene = new Scene(root, 900.0, 600.0);
            menu.showMenu();
            menu.showSettingsPage();
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            menu.confirmUnsavedChanges(() -> { }, () -> { });
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Button back = menu.backButtonForTest();
            Button settingsSave = menu.saveSettingsButtonForTest();
            assertTrue(back.getStyleClass().contains("main-menu-back-button"));
            assertTrue(settingsSave.getStyleClass().contains("main-menu-settings-save-button"));
            assertFalse(settingsSave.getStyleClass().contains("main-menu-button"));
            assertTrue(menu.getStylesheets().stream()
                    .anyMatch(path -> path.endsWith("/main-menu-settings-save.css")));
            assertContentSized(back);
            assertContentSized(settingsSave);

            Button yes = menu.saveChangesButtonForTest();
            Button no = menu.discardChangesButtonForTest();
            assertTrue(yes.getStyleClass().contains("unsaved-action-button"));
            assertTrue(no.getStyleClass().contains("unsaved-action-button"));
            assertEquals(yes.getStyleClass(), no.getStyleClass(),
                    "Кнопки підтвердження мають однаковий набір класів оформлення");
            assertEquals(yes.getFont(), no.getFont(), "Обидві кнопки мають використовувати однаковий шрифт");
            assertEquals(yes.getPrefWidth(), no.getPrefWidth(), 0.1);
            assertEquals(yes.getPrefHeight(), no.getPrefHeight(), 0.1);
            assertEquals(yes.getMinWidth(), no.getMinWidth(), 0.1);
            assertEquals(yes.getMinHeight(), no.getMinHeight(), 0.1);
            assertEquals(Region.USE_COMPUTED_SIZE, yes.getMaxWidth(), 0.1);
            assertEquals(Region.USE_COMPUTED_SIZE, no.getMaxHeight(), 0.1);
            assertEquals(yes.getWidth(), no.getWidth(), 0.5);
            assertEquals(yes.getHeight(), no.getHeight(), 0.5);
            menu.hide();
            menu.dispose();
        });
    }

    /** Перевіряє, що жоден явний min/pref/max розмір не фіксує розміри кнопки. */
    private static void assertContentSized(Button button) {
        assertEquals(Region.USE_COMPUTED_SIZE, button.getMinWidth(), 0.1);
        assertEquals(Region.USE_COMPUTED_SIZE, button.getPrefWidth(), 0.1);
        assertEquals(Region.USE_COMPUTED_SIZE, button.getMaxWidth(), 0.1);
        assertEquals(Region.USE_COMPUTED_SIZE, button.getMinHeight(), 0.1);
        assertEquals(Region.USE_COMPUTED_SIZE, button.getPrefHeight(), 0.1);
        assertEquals(Region.USE_COMPUTED_SIZE, button.getMaxHeight(), 0.1);
    }

    /** Виконує перевірку в JavaFX Application Thread та передає помилку назад до JUnit. */
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
        assertTrue(latch.await(5, TimeUnit.SECONDS), "UI-тест має завершитися вчасно");
        if (failure[0] != null) {
            throw new AssertionError("Перевірка кнопок підтвердження завершилася помилкою", failure[0]);
        }
    }
}
