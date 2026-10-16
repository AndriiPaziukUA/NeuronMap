package com.example.neuronmap.view.menu;

import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.settings.SettingsEditSession;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє координацію збереження й відхилення змін контролером головного меню. */
final class MainMenuUnsavedChangesWorkflowTest {

    /** Запускає JavaFX toolkit перед перевірками користувацьких сценаріїв меню. */
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

    /** Перевіряє підтвердження перейменування перед поверненням зі сторінки списку проєктів. */
    @Test
    void confirmingRenameBeforeBackCommitsRenameAndContinuesNavigation() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<List<ProjectDescriptor>> catalog = new AtomicReference<>(List.of(project("project", "Old")));
            AtomicInteger renameCalls = new AtomicInteger();
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(
                    view, localization, catalog, renameCalls, new AtomicInteger());
            controller.open();
            view.showLoadPage(catalog.get());
            SavedProjectRowView row = view.projectRowsForTest().get(0);
            row.beginRenameForTest();
            row.renameFieldForTest().setText("Renamed");

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            assertTrue(view.isLoadPage(), "Сторінка не має змінитися до відповіді в діалозі");
            view.saveChangesButtonForTest().fire();

            assertFalse(view.isConfirmingUnsavedChanges());
            assertFalse(view.isLoadPage(), "Після підтвердження навігація має продовжитися");
            assertEquals(1, renameCalls.get());
            assertEquals("Renamed", catalog.get().get(0).name());
            controller.dispose(null);
        });
    }

    /** Перевіряє, що відхилення зміни назви не викликає каталог і дозволяє повернутися назад. */
    @Test
    void discardingRenameCancelsEditorWithoutCallingRenameOperation() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<List<ProjectDescriptor>> catalog = new AtomicReference<>(List.of(project("project", "Old")));
            AtomicInteger renameCalls = new AtomicInteger();
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(
                    view, localization, catalog, renameCalls, new AtomicInteger());
            controller.open();
            view.showLoadPage(catalog.get());
            SavedProjectRowView row = view.projectRowsForTest().get(0);
            row.beginRenameForTest();
            row.renameFieldForTest().setText("Never saved");

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();

            assertEquals(0, renameCalls.get());
            assertEquals("Old", catalog.get().get(0).name());
            assertFalse(view.isConfirmingUnsavedChanges());
            assertFalse(view.isLoadPage());
            controller.dispose(null);
        });
    }

    /** Перевіряє, що невдала валідація назви не дозволяє залишити сторінку після натискання «Так». */
    @Test
    void invalidRenameKeepsNavigationBlockedUntilDiscarded() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<List<ProjectDescriptor>> catalog = new AtomicReference<>(List.of(project("project", "Old")));
            AtomicInteger renameCalls = new AtomicInteger();
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(
                    view, localization, catalog, renameCalls, new AtomicInteger());
            controller.open();
            view.showLoadPage(catalog.get());
            SavedProjectRowView row = view.projectRowsForTest().get(0);
            row.beginRenameForTest();
            row.renameFieldForTest().setText("   ");

            view.backButtonForTest().fire();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.saveChangesButtonForTest().fire();

            assertTrue(view.isLoadPage(), "Перехід не має відбутися, поки назва некоректна");
            assertTrue(view.isConfirmingUnsavedChanges(), "Потрібно повторно показати діалог для невирішеної зміни");
            assertEquals(0, renameCalls.get());
            view.discardChangesButtonForTest().fire();

            assertFalse(view.isLoadPage());
            assertEquals(0, renameCalls.get());
            controller.dispose(null);
        });
    }

    /** Перевіряє збереження одночасних змін проєкту й налаштування під час закриття меню. */
    @Test
    void closingMenuConfirmsProjectAndSettingChangesTogether() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<List<ProjectDescriptor>> catalog = new AtomicReference<>(List.of(project("project", "Old")));
            AtomicInteger renameCalls = new AtomicInteger();
            AtomicInteger resumeCalls = new AtomicInteger();
            AtomicReference<String> activeTheme = new AtomicReference<>("light");
            AtomicReference<String> persistedTheme = new AtomicReference<>("light");
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(view, localization, catalog, renameCalls, resumeCalls);
            SettingsEditSession.Setting<String> theme = controller.registerSetting(
                    activeTheme::get, activeTheme::set, persistedTheme::set);
            controller.open();
            view.showLoadPage(catalog.get());
            SavedProjectRowView row = view.projectRowsForTest().get(0);
            row.beginRenameForTest();
            row.renameFieldForTest().setText("New name");
            controller.previewSetting(theme, "dark");

            controller.close();
            assertTrue(view.isConfirmingUnsavedChanges());
            assertTrue(view.isMenuVisible(), "Меню лишається відкритим до розв'язання змін");
            view.saveChangesButtonForTest().fire();

            assertFalse(view.isMenuVisible());
            assertEquals("New name", catalog.get().get(0).name());
            assertEquals(1, renameCalls.get());
            assertEquals("dark", activeTheme.get());
            assertEquals("dark", persistedTheme.get());
            assertEquals(1, resumeCalls.get());
            controller.dispose(null);
        });
    }

    /** Перевіряє, що після помилки підтвердження сесія все ще може відкотити всі тимчасові значення. */
    @Test
    void commitFailureLeavesSettingsAvailableForRollback() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            MainMenuController controller = createController(
                    view,
                    localization,
                    new AtomicReference<>(List.of()),
                    new AtomicInteger(),
                    new AtomicInteger());
            AtomicReference<String> activeFirst = new AtomicReference<>("first-original");
            AtomicReference<String> activeSecond = new AtomicReference<>("second-original");
            AtomicReference<String> persistedFirst = new AtomicReference<>("first-original");
            AtomicReference<String> persistedSecond = new AtomicReference<>("second-original");
            SettingsEditSession.Setting<String> failingSetting = controller.registerSetting(
                    activeFirst::get,
                    activeFirst::set,
                    ignored -> { throw new IllegalStateException("Помилка збереження налаштування"); });
            SettingsEditSession.Setting<String> stableSetting = controller.registerSetting(
                    activeSecond::get, activeSecond::set, persistedSecond::set);
            controller.open();
            view.showSettingsPage();
            controller.previewSetting(failingSetting, "first-preview");
            controller.previewSetting(stableSetting, "second-preview");

            controller.close();
            assertTrue(view.isConfirmingUnsavedChanges());
            assertThrows(IllegalStateException.class, () -> view.saveChangesButtonForTest().fire());
            assertEquals("first-preview", activeFirst.get());
            assertEquals("second-preview", activeSecond.get());

            controller.close();
            assertTrue(view.isConfirmingUnsavedChanges());
            view.discardChangesButtonForTest().fire();
            assertEquals("first-original", activeFirst.get());
            assertEquals("second-original", activeSecond.get());
            assertEquals("first-original", persistedFirst.get());
            assertEquals("second-original", persistedSecond.get());
            assertFalse(view.hasUnsavedChanges());
            controller.dispose(null);
        });
    }

    /** Створює контролер з каталогом, що оновлюється під час перейменування. */
    private static MainMenuController createController(
            MainMenuView view,
            LocalizationService localization,
            AtomicReference<List<ProjectDescriptor>> catalog,
            AtomicInteger renameCalls,
            AtomicInteger resumeCalls
    ) {
        return new MainMenuController(
                view,
                localization,
                catalog::get,
                () -> { },
                ignored -> { },
                (original, name) -> {
                    renameCalls.incrementAndGet();
                    ProjectDescriptor renamed = new ProjectDescriptor(
                            original.id(), name, original.databasePath(), Instant.now());
                    catalog.updateAndGet(projects -> projects.stream()
                            .map(project -> project.id().equals(original.id()) ? renamed : project)
                            .toList());
                    return renamed;
                },
                ignored -> { },
                () -> { },
                () -> { },
                resumeCalls::incrementAndGet
        );
    }

    /** Створює опис проєкту для сценаріїв перейменування. */
    private static ProjectDescriptor project(String id, String name) {
        return new ProjectDescriptor(id, name, Path.of(id + ".db"), Instant.now());
    }

    /** Виконує дію в потоці JavaFX і передає її помилку до JUnit. */
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
            throw new AssertionError("Тест сценарію меню завершився помилкою", failure[0]);
        }
    }
}
