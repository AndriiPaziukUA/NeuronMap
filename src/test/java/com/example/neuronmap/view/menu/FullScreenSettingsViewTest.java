package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє локалізацію та передавання змін стилізованого перемикача повноекранного режиму. */
final class FullScreenSettingsViewTest {

    /** Запускає JavaFX toolkit один раз перед тестуванням елементів керування. */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(latch::countDown);
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /** Перевіряє прев'ю вибраного стану та візуальне відображення поточного значення. */
    @Test
    void togglePreviewsValueAndReflectsCurrentState() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            AtomicBoolean fullScreen = new AtomicBoolean(false);
            FullScreenSettingsView view = new FullScreenSettingsView(localization);
            view.setActions(fullScreen::get, fullScreen::set);

            assertFalse(view.toggleForTest().isSelected());
            view.toggleForTest().fire();
            assertTrue(fullScreen.get());
            assertTrue(view.toggleForTest().isSelected());

            fullScreen.set(false);
            view.refreshValue();
            assertFalse(view.toggleForTest().isSelected());
            view.dispose();
        });
    }

    /** Перевіряє підпис налаштування для англійської, української та російської локалей. */
    @Test
    void labelIsLocalizedForEverySupportedLanguage() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            FullScreenSettingsView view = new FullScreenSettingsView(localization);

            assertEquals("Full-screen mode:", ((javafx.scene.control.Label) view.getChildren().get(0)).getText());
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);
            assertEquals("Повноекранний режим:", ((javafx.scene.control.Label) view.getChildren().get(0)).getText());
            localization.previewLanguage(SupportedLanguage.RUSSIAN);
            assertEquals("Полноэкранный режим:", ((javafx.scene.control.Label) view.getChildren().get(0)).getText());
            view.dispose();
        });
    }

    /** Перевіряє, що активний стан перемикача використовує синій акцент замість попереднього зеленого. */
    @Test
    void selectedTrackUsesBlueAccent() throws Exception {
        try (var stylesheet = getClass().getResourceAsStream("/menu-enhancements.css")) {
            assertTrue(stylesheet != null, "Таблиця стилів меню має бути присутня в ресурсах");
            String css = new String(stylesheet.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(css.contains("-fx-fill: #245bd7;"));
            assertFalse(css.contains("-fx-fill: #247c62;"));
        }
    }

    /** Перевіряє, що перемикач і вибір мови вирівнюються праворуч у спільному рядку налаштувань. */
    @Test
    void settingControlsAlignToTheRightEdge() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            FullScreenSettingsView fullScreen = new FullScreenSettingsView(localization);
            LanguageSettingsView language = new LanguageSettingsView(localization);
            fullScreen.resize(600.0, 40.0);
            fullScreen.layout();

            javafx.scene.layout.HBox languageRow = (javafx.scene.layout.HBox) language.getChildren().get(0);
            languageRow.resize(600.0, 40.0);
            languageRow.layout();

            assertEquals(600.0, fullScreen.getWidth(), 0.01);
            assertEquals(600.0, languageRow.getWidth(), 0.01);
            assertEquals(fullScreen.getWidth(),
                    fullScreen.toggleForTest().getLayoutX() + fullScreen.toggleForTest().getWidth(), 1.0);
            assertEquals(languageRow.getWidth(),
                    language.languageSelectorForTest().getLayoutX()
                            + language.languageSelectorForTest().getWidth(), 1.0);
            assertTrue(((javafx.scene.control.Label) fullScreen.getChildren().get(0)).getLayoutX()
                    < fullScreen.toggleForTest().getLayoutX());
            assertTrue(((javafx.scene.control.Label) languageRow.getChildren().get(0)).getLayoutX()
                    < language.languageSelectorForTest().getLayoutX());

            fullScreen.dispose();
            language.dispose();
        });
    }

    /** Виконує перевірку в JavaFX Application Thread і передає винятки назад до JUnit-потоку. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                failure[0] = exception;
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
