package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.project.ProjectDescriptor;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

/** Перевіряє вибір збережених проєктів, відкриття подвійним кліком і стилі постійного вибору. */
final class ProjectListViewTest {

    /** Запускає JavaFX toolkit перед створенням компонентів списку. */
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

    /** Переконується, що одинарний клік вибирає рядок, але не відкриває проєкт. */
    @Test
    void singleClickSelectsProjectWithoutOpeningIt() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<ProjectDescriptor> openedProject = new AtomicReference<>();
            ProjectListView list = createList(openedProject);
            try {
                List<SavedProjectRowView> rows = list.rowsForTest();
                fireMouseClick(rows.get(0), 1);
                assertTrue(rows.get(0).isSelected());
                assertFalse(rows.get(1).isSelected());
                assertTrue(rows.get(0).getStyleClass().contains("selected"));
                assertNull(openedProject.get());
            } finally {
                list.dispose();
            }
        });
    }

    /** Переконується, що вибір лишається на рядку до кліку по іншому та не накопичується. */
    @Test
    void clickingAnotherProjectMovesThePersistentSelection() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<ProjectDescriptor> openedProject = new AtomicReference<>();
            ProjectListView list = createList(openedProject);
            try {
                List<SavedProjectRowView> rows = list.rowsForTest();
                fireMouseClick(rows.get(0), 1);
                fireMouseClick(rows.get(1), 1);
                assertFalse(rows.get(0).isSelected());
                assertFalse(rows.get(0).getStyleClass().contains("selected"));
                assertTrue(rows.get(1).isSelected());
                assertTrue(rows.get(1).getStyleClass().contains("selected"));
                assertNull(openedProject.get());
            } finally {
                list.dispose();
            }
        });
    }

    /** Переконується, що подвійний клік відкриває саме натиснутий проєкт після його вибору. */
    @Test
    void doubleClickOpensTheClickedProject() throws Exception {
        runOnFxThread(() -> {
            AtomicReference<ProjectDescriptor> openedProject = new AtomicReference<>();
            ProjectListView list = createList(openedProject);
            try {
                List<SavedProjectRowView> rows = list.rowsForTest();
                fireMouseClick(rows.get(1), 2);
                assertTrue(rows.get(1).isSelected());
                assertFalse(rows.get(0).isSelected());
                assertNotNull(openedProject.get(), "Подвійний клік має викликати відкриття проєкту");
                assertEquals("project-two", openedProject.get().id());
            } finally {
                list.dispose();
            }
        });
    }

    /** Перевіряє окремі стани вибору й наведення у власній таблиці стилів списку проєктів. */
    @Test
    void stylesheetDistinguishesSelectedAndHoveredRows() throws Exception {
        try (InputStream stream = ProjectListView.class.getResourceAsStream("/project-list.css")) {
            assertNotNull(stream, "Таблиця стилів списку проєктів має бути доступною в classpath");
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains(".saved-project-row.selected {"));
            assertTrue(css.contains(".saved-project-row.selected:hover {"));
            assertTrue(css.indexOf(".saved-project-row.selected {")
                    < css.indexOf(".saved-project-row.selected:hover {"));
        }
    }

    /** Створює список двох проєктів із callback-ом відкриття, який записує відкритий дескриптор. */
    private static ProjectListView createList(AtomicReference<ProjectDescriptor> openedProject) {
        ProjectListView list = new ProjectListView(new LocalizationService(Locale.ENGLISH));
        list.showProjects(
                List.of(project("project-one", "Перший проєкт"), project("project-two", "Другий проєкт")),
                openedProject::set,
                (original, name) -> new ProjectDescriptor(
                        original.id(), name, original.databasePath(), original.modifiedAt()),
                ignored -> { });
        return list;
    }

    /** Створює стабільний дескриптор проєкту без залежності від файлової системи. */
    private static ProjectDescriptor project(String id, String name) {
        return new ProjectDescriptor(
                id, name, Path.of(id + ".db"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** Передає рядку подію кліку з заданою кількістю натискань через механізм подій JavaFX. */
    private static void fireMouseClick(SavedProjectRowView row, int clickCount) {
        MouseEvent event = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                1.0, 1.0, 1.0, 1.0,
                MouseButton.PRIMARY, clickCount,
                false, false, false, false,
                true, false, false,
                false, false, true, null
        );
        Event.fireEvent(row, event);
    }

    /** Виконує дію в JavaFX Application Thread і передає помилку тесту до потоку JUnit. */
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
            throw new AssertionError("Тест списку проєктів завершився помилкою", failure[0]);
        }
    }
}
