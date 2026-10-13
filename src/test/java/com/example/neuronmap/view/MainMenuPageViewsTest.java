package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє спеціалізовані подання сторінок головного меню окремо одне від одного. */
final class MainMenuPageViewsTest {

    /** Запускає JavaFX toolkit перед виконанням перевірок елементів інтерфейсу. */
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

    /** Перевіряє написи, дії та звільнення обробників головної сторінки. */
    @Test
    void homeViewDisplaysLocalizedButtonsAndAcceptsActions() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            AtomicReference<String> invoked = new AtomicReference<>();
            MainMenuHomeView view = new MainMenuHomeView(
                    localization,
                    () -> invoked.set("continue"),
                    () -> invoked.set("new-project"),
                    () -> invoked.set("load"),
                    () -> invoked.set("settings"),
                    () -> invoked.set("exit")
            );

            assertEquals(List.of(
                    "Продовжити", "Новий проєкт", "Завантажити", "Налаштування", "Вихід"
            ), view.buttonTexts());
            assertEquals(5, view.getChildren().size());
            view.dispose();
        });
    }

    /** Перевіряє, що сторінка проєктів показує порожній список і створює прокручувану область. */
    @Test
    void projectListViewHandlesEmptyCatalog() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            ProjectListView view = new ProjectListView(localization);
            view.showProjects(List.of(), ignored -> { }, (project, name) -> project, ignored -> { });

            assertNotNull(view.scrollPaneForTest());
            assertNotNull(view.scrollPaneForTest().getContent());
            view.dispose();
        });
    }

    /** Перевіряє, що вибір мови передається callback-у через ComboBox стандартного JavaFX. */
    @Test
    void languageSettingsViewDelegatesSelectionToCallback() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            AtomicReference<SupportedLanguage> selectedByCallback = new AtomicReference<>();
            LanguageSettingsView view = new LanguageSettingsView(localization);
            view.setLanguageSelectedAction(selectedByCallback::set);

            ComboBox<SupportedLanguage> selector = view.languageSelectorForTest();
            assertNotNull(selector);
            assertEquals(localization.supportedLanguagesInDisplayOrder(), selector.getItems());

            SupportedLanguage alternative = selector.getItems().stream()
                    .filter(language -> language != localization.language())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Очікується щонайменше дві доступні мови"));
            selector.setValue(alternative);
            selector.getOnAction().handle(new ActionEvent(selector, null));
            assertEquals(alternative, selectedByCallback.get());
        });
    }

    /** Виконує дію в потоці JavaFX і передає помилки назад у потік тесту. */
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
            throw new AssertionError("Тест компонента головного меню завершився помилкою", failure[0]);
        }
    }
}
