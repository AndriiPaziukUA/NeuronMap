package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє структуру головного меню та поведінку редагування налаштувань.
 */
final class MainMenuViewTest {

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     */
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

    /**
     * Перевіряє очікувану поведінку: містить і кнопка.
     */
    @Test
    void mainPageContainsFourVerticalActionsAndBackButton() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization =
                    new LocalizationService(Locale.forLanguageTag("uk"));
            MainMenuView view = new MainMenuView(localization, ignored -> { });

            assertEquals(
                    List.of(
                            "Новий проєкт",
                            "Завантажити",
                            "Налаштування",
                            "Вихід"
                    ),
                    view.mainButtonTextsForTest()
            );
            assertEquals("Назад", view.backButtonForTest().getText());

            view.showMenu();
            assertTrue(view.isMenuVisible());
            view.hide();
            assertFalse(view.isMenuVisible());
            view.dispose();
        });
    }

    /**
     * Перевіряє очікувану поведінку: налаштування мова змінити.
     */
    @Test
    void settingsLanguageChangeIsImmediateButCanBeDiscarded() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization =
                    new LocalizationService(Locale.forLanguageTag("uk"));
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            view.showSettingsPage();

            ComboBox<SupportedLanguage> selector = view.languageSelectorForTest();
            selector.setValue(SupportedLanguage.RUSSIAN);
            selector.getOnAction().handle(new ActionEvent(selector, null));
            assertEquals(SupportedLanguage.RUSSIAN, localization.language());
            assertTrue(view.hasUnsavedChanges());

            view.discardUnsavedChanges();
            assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
            assertFalse(view.hasUnsavedChanges());
            view.dispose();
        });
    }

    /**
     * Перевіряє очікувану поведінку: проєкт створює очікуваний змінити.
     */
    @Test
    void renamingProjectCreatesPendingChangeUntilCommitted() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization =
                    new LocalizationService(Locale.forLanguageTag("uk"));
            AtomicReference<ProjectDescriptor> renamed = new AtomicReference<>();
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            ProjectDescriptor project = new ProjectDescriptor(
                    "id",
                    "Project",
                    Path.of("project.db"),
                    Instant.now()
            );
            view.setActions(new MainMenuView.Actions(
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    (original, name) -> {
                        ProjectDescriptor result = new ProjectDescriptor(
                                original.id(),
                                name,
                                original.databasePath(),
                                Instant.now()
                        );
                        renamed.set(result);
                        return result;
                    },
                    ignored -> { },
                    () -> { }
            ));
            view.showLoadPage(List.of(project));
            SavedProjectRowView row = view.projectRowsForTest().get(0);

            row.beginRenameForTest();
            row.renameFieldForTest().setText("Renamed");
            assertTrue(view.hasUnsavedChanges());

            view.saveUnsavedChanges();
            assertFalse(view.hasUnsavedChanges());
            assertEquals("Renamed", renamed.get().name());
            assertEquals("Renamed", row.project().name());
            view.dispose();
        });
    }

    /**
     * Запускає або планує дію, повʼязану з «відповідну операцію».
     *
     * @param action дія, яку потрібно виконати.
     */
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
            /**
             * Повертає результат операції «відповідну операцію».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
