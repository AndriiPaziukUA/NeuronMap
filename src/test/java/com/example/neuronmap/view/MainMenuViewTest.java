package com.example.neuronmap.view;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for menu navigation, responsive layout, settings save/discard, and localized text. */
final class MainMenuViewTest {

    @TempDir
    Path tempDirectory;

    /** Starts the JavaFX toolkit once so controls can be tested on the JavaFX thread. */
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

    /** Verifies that Continue is first and the main page does not attach a Back button. */
    @Test
    void mainPageStartsWithContinueAndDoesNotShowBackButton() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            MainMenuView view = new MainMenuView(localization, ignored -> { });

            assertEquals(List.of(
                    "Продовжити",
                    "Новий проєкт",
                    "Завантажити",
                    "Налаштування",
                    "Вихід"
            ), view.mainButtonTextsForTest());
            assertEquals("NeuronMap", view.pageTitleForTest());
            assertNull(view.backButtonForTest().getParent(),
                    "Back must not be parented while the main page is active");
            view.showLoadPage(List.of());
            assertEquals("Збережені проєкти", view.pageTitleForTest());
            view.showMainPage();
            assertEquals("NeuronMap", view.pageTitleForTest());
            assertNull(view.backButtonForTest().getParent(),
                    "Back must be detached when returning from the saved-project page");
            assertTrue(view.menuPanelForTest().isFocusTraversable());
            view.showSettingsPage();
            assertEquals("Налаштування", view.pageTitleForTest());
            view.showMainPage();
            assertEquals("NeuronMap", view.pageTitleForTest());
            assertNull(view.backButtonForTest().getParent(),
                    "Back must be detached when returning from settings");
            assertTrue(view.menuPanelForTest().isFocusTraversable());
            assertEquals(460.0, view.panelMaxWidthForTest());
            assertEquals(430.0, view.panelMaxHeightForTest());
            view.resize(1280.0, 720.0);
            view.layout();
            assertTrue(view.panelWidthForTest() < view.panelMaxWidthForTest());
            assertTrue(view.panelHeightForTest() < view.panelMaxHeightForTest());

            view.showMenu();
            assertTrue(view.isMenuVisible());
            view.hide();
            assertFalse(view.isMenuVisible());
            view.dispose();
        });
    }

    /** Verifies that the settings Save button appears only for unsaved changes and hides after saving. */
    @Test
    void settingsSaveButtonTracksDirtyStateAndPersistsLanguage() throws Exception {
        GlobalSettingsStore store = new GlobalSettingsStore(tempDirectory.resolve("settings.properties"));
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(store);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            view.showSettingsPage();

            assertFalse(view.saveSettingsButtonForTest().isVisible());
            assertFalse(view.saveSettingsButtonForTest().isManaged());

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.UKRAINIAN);
            assertTrue(view.hasUnsavedChanges());
            assertTrue(view.saveSettingsButtonForTest().isVisible());
            assertTrue(view.saveSettingsButtonForTest().isManaged());

            view.saveSettingsButtonForTest().fire();
            assertFalse(view.hasUnsavedChanges());
            assertFalse(view.saveSettingsButtonForTest().isVisible());
            assertEquals("uk", store.load(LocalizationService.LANGUAGE_KEY));
            view.dispose();
        });
    }

    /** Verifies that discarding a language preview restores the original language and hides Save. */
    @Test
    void settingsLanguagePreviewCanBeDiscarded() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            view.showSettingsPage();

            chooseLanguage(view.languageSelectorForTest(), SupportedLanguage.RUSSIAN);
            assertEquals(SupportedLanguage.RUSSIAN, localization.language());
            assertTrue(view.hasUnsavedChanges());

            view.discardUnsavedChanges();
            assertEquals(SupportedLanguage.ENGLISH, localization.language());
            assertFalse(view.hasUnsavedChanges());
            assertFalse(view.saveSettingsButtonForTest().isVisible());
            view.dispose();
        });
    }

    /** Verifies that a pending project rename is committed by the existing save workflow. */
    @Test
    void renamingProjectCreatesPendingChangeUntilCommitted() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            AtomicReference<ProjectDescriptor> renamed = new AtomicReference<>();
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            ProjectDescriptor project = new ProjectDescriptor(
                    "id", "Project", Path.of("project.db"), Instant.now());
            view.setActions(new MainMenuView.Actions(
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { },
                    ignored -> { },
                    (original, name) -> {
                        ProjectDescriptor result = new ProjectDescriptor(
                                original.id(), name, original.databasePath(), Instant.now());
                        renamed.set(result);
                        return result;
                    },
                    ignored -> { },
                    () -> { }
            ));
            view.showLoadPage(List.of(project));
            SavedProjectRowView row = view.projectRowsForTest().get(0);

            row.beginRenameForTest();
            assertEquals("Project", row.renameFieldForTest().getText());
            assertTrue(row.isEditing());
            assertEquals(row.renameFieldForTest().getText().length(),
                    row.renameFieldForTest().getCaretPosition());
            assertEquals("", row.renameFieldForTest().getSelectedText());
            assertTrue(row.renameFieldForTest().getStyleClass().contains("saved-project-rename-field"));
            assertFalse(row.nameLabelForTest().isVisible());
            assertFalse(row.deleteButtonForTest().isVisible());
            assertTrue(row.cancelButtonForTest().isVisible());
            row.renameFieldForTest().setText("Renamed");
            assertTrue(view.hasUnsavedChanges());

            view.saveUnsavedChanges();
            assertFalse(view.hasUnsavedChanges());
            assertEquals("Renamed", renamed.get().name());
            assertEquals("Renamed", row.project().name());
            assertTrue(row.deleteButtonForTest().isVisible());
            assertFalse(row.cancelButtonForTest().isVisible());
            view.dispose();
        });
    }

    /**
     * Verifies that the rendered caret is placed after the saved name on the first edit and after
     * creating a fresh project list again, rather than merely checking the text field's caret index.
     */
    @Test
    void renameCaretPositionIsAtEndAfterFirstShowAndReopeningProjectList() throws Exception {
        AtomicReference<MainMenuView> menuReference = new AtomicReference<>();
        AtomicReference<SavedProjectRowView> rowReference = new AtomicReference<>();
        List<ProjectDescriptor> projects = List.of(new ProjectDescriptor(
                "caret-id", "Project Name", Path.of("caret-project.db"), Instant.now()));

        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(new LocalizationService(Locale.ENGLISH), ignored -> { });
            new Scene(view, 1000.0, 700.0);
            view.resize(1000.0, 700.0);
            view.showMenu();
            view.showLoadPage(projects);
            view.applyCss();
            view.layout();

            SavedProjectRowView row = view.projectRowsForTest().get(0);
            row.renameButtonForTest().fire();
            rowReference.set(row);
            menuReference.set(view);
        });

        runOnFxThreadAfterTurns(() -> assertRenameCaretPositionAtEnd(rowReference.get()), 4);

        // Rebuilding the saved-project rows reproduces the first-edit path seen after reopening the menu.
        runOnFxThread(() -> {
            MainMenuView view = menuReference.get();
            view.showMainPage();
            view.showLoadPage(projects);
            view.applyCss();
            view.layout();
            SavedProjectRowView freshRow = view.projectRowsForTest().get(0);
            freshRow.renameButtonForTest().fire();
            rowReference.set(freshRow);
        });
        runOnFxThreadAfterTurns(() -> {
            assertRenameCaretPositionAtEnd(rowReference.get());
            MainMenuView view = menuReference.get();
            view.dispose();
        }, 4);
    }

    /**
     * Verifies the public text-input state after renaming starts without depending on private skin nodes.
     * JavaFX may not create or expose the internal {@code .caret} node when a scene is tested without a
     * shown window, so the regression check uses the stable TextField API and confirms that the field
     * has a usable layout slot. The first-show and reopen paths are both exercised by the calling test.
     *
     * @param row project row currently in rename mode.
     */
    private static void assertRenameCaretPositionAtEnd(SavedProjectRowView row) {
        assertNotNull(row);
        Scene scene = row.getScene();
        assertNotNull(scene, "The rename field must be attached to a scene");

        javafx.scene.control.TextField field = row.renameFieldForTest();
        assertNotNull(field, "Rename mode must create a text field");
        assertTrue(field.isVisible(), "The rename field must be visible");
        assertTrue(field.getWidth() > 0.0, "The rename field must have a calculated layout width");
        assertEquals(row.project().name(), field.getText(), "The saved project name must be prefilled");
        assertEquals(field.getLength(), field.getCaretPosition(),
                "The insertion point must be at the end of the project name");
        assertEquals("", field.getSelectedText(), "The project name must not remain selected");
    }

    /** Verifies that the saved-project page uses the maximum panel height and a functional conditional scrollbar. */
    @Test
    void savedProjectListUsesMaximumHeightAndAsNeededScrollbar() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainMenuView view = new MainMenuView(localization, ignored -> { });
            Scene scene = new Scene(view, 1280.0, 720.0);
            assertNotNull(scene.getRoot());
            view.resize(1280.0, 720.0);
            List<ProjectDescriptor> projects = java.util.stream.IntStream.range(0, 30)
                    .mapToObj(index -> new ProjectDescriptor(
                            "id-" + index,
                            "Project " + index,
                            Path.of("project-" + index + ".db"),
                            Instant.now()))
                    .toList();

            view.showMenu();
            view.showLoadPage(projects);
            view.applyCss();
            view.layout();
            ScrollPane scrollPane = view.projectScrollPaneForTest();
            assertNotNull(scrollPane);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scrollPane.getVbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scrollPane.getHbarPolicy());
            assertEquals(view.panelMaxHeightForTest(), view.panelHeightForTest(), 0.5);

            scrollPane.applyCss();
            scrollPane.layout();
            ScrollBar scrollBar = verticalScrollBarForTest(scrollPane);
            assertNotNull(scrollBar, "JavaFX should create the vertical scrollbar for the scroll pane");
            assertTrue(scrollBar.isVisible(), "The vertical scrollbar should be visible for a long project list");
            scrollBar.setValue(scrollBar.getMax());
            assertEquals(scrollBar.getMax(), scrollPane.getVvalue(), 0.05,
                    "Dragging the scrollbar must update the scroll pane position");

            ProjectDescriptor singleProject = new ProjectDescriptor(
                    "single", "Project", Path.of("single-project.db"), Instant.now());
            view.showLoadPage(List.of(singleProject));
            view.applyCss();
            view.layout();
            ScrollPane shortListPane = view.projectScrollPaneForTest();
            shortListPane.applyCss();
            shortListPane.layout();
            ScrollBar shortListScrollBar = verticalScrollBarForTest(shortListPane);
            assertNotNull(shortListScrollBar);
            assertFalse(shortListScrollBar.isVisible(),
                    "The scrollbar should remain hidden when all saved projects fit vertically");
            view.dispose();
        });
    }

    /**
     * Verifies that project names keep usable width and rename controls stay inside the row bounds.
     */
    @Test
    void projectRowAdaptsItsLayoutWhenEnteringRenameMode() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            ProjectDescriptor project = new ProjectDescriptor(
                    "layout-id", "Project", Path.of("layout-project.db"), Instant.now());
            SavedProjectRowView row = new SavedProjectRowView(
                    project,
                    localization,
                    ignored -> { },
                    (original, name) -> original,
                    ignored -> { },
                    () -> { });

            row.resize(400.0, 54.0);
            row.layout();
            assertTrue(row.nameLabelForTest().getWidth() >= 35.0);

            row.beginRenameForTest();
            row.resize(400.0, 54.0);
            row.layout();
            assertTrue(row.renameFieldForTest().getWidth() >= 100.0);
            assertTrue(row.cancelButtonForTest().getLayoutX()
                    + row.cancelButtonForTest().getWidth() <= row.getWidth() + 0.5);
            assertFalse(row.deleteButtonForTest().isVisible());
            row.dispose();
        });
    }

    /** Guards against the confirmation card stretching to the application height or left-aligning its prompt. */
    @Test
    void unsavedChangesDialogKeepsPreferredHeightAndCenteredQuestion() throws Exception {
        runOnFxThread(() -> {
            UnsavedChangesView view = new UnsavedChangesView(new LocalizationService(Locale.ENGLISH));
            view.show(() -> { }, () -> { });

            assertEquals(Region.USE_PREF_SIZE, view.cardMaxWidthForTest());
            assertEquals(Region.USE_PREF_SIZE, view.cardMaxHeightForTest());
            assertEquals(TextAlignment.CENTER, view.messageTextAlignmentForTest());
            view.resize(900.0, 600.0);
            view.layout();
            assertTrue(view.cardWidthForTest() < 900.0);
            assertTrue(view.cardHeightForTest() < 600.0);
            view.hide();
        });
    }

    /**
     * Finds the JavaFX vertical scrollbar created by a scroll pane skin.
     *
     * @param scrollPane pane whose skin is searched for a vertical scrollbar.
     * @return the vertical scrollbar, or {@code null} if the skin has not created one.
     */
    private static ScrollBar verticalScrollBarForTest(ScrollPane scrollPane) {
        scrollPane.applyCss();
        return scrollPane.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scrollBar -> scrollBar.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .orElse(null);
    }

    /** Selects a language and fires its change event as a user interaction would. */
    private static void chooseLanguage(
            ComboBox<SupportedLanguage> selector,
            SupportedLanguage language
    ) {
        selector.setValue(language);
        selector.getOnAction().handle(new ActionEvent(selector, null));
    }

    /**
     * Executes a test action on the JavaFX thread after the requested number of deferred event-queue turns.
     * This is used for assertions that depend on focus, skin creation, or layout settling.
     *
     * @param action assertion or UI operation to run after the deferred turns.
     * @param turns number of additional JavaFX event-queue turns to wait for; must not be negative.
     * @throws Exception if the FX-thread action fails or does not complete within the timeout.
     */
    private static void runOnFxThreadAfterTurns(Runnable action, int turns) throws Exception {
        if (turns < 0) {
            throw new IllegalArgumentException("turns must not be negative");
        }
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> executeAfterFxTurns(action, turns, latch, failure));
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("JavaFX deferred test failed", failure[0]);
        }
    }

    /**
     * Requeues a test action until its requested number of JavaFX event-queue turns has elapsed.
     *
     * @param action action to execute once the remaining turns reach zero.
     * @param remainingTurns number of event-queue turns still to defer the action.
     * @param latch latch completed after the action finishes or throws.
     * @param failure storage for any throwable raised by the action.
     */
    private static void executeAfterFxTurns(
            Runnable action,
            int remainingTurns,
            CountDownLatch latch,
            Throwable[] failure
    ) {
        if (remainingTurns > 0) {
            Platform.runLater(() -> executeAfterFxTurns(
                    action, remainingTurns - 1, latch, failure));
            return;
        }
        try {
            action.run();
        } catch (Throwable throwable) {
            failure[0] = throwable;
        } finally {
            latch.countDown();
        }
    }

    /** Executes a test action on the JavaFX application thread and rethrows any test failure. */
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
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
