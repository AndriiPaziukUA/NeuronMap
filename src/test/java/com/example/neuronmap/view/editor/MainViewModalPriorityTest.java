package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.menu.MainMenuView;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє пріоритет головного меню над динамічними накладками редактора. */
final class MainViewModalPriorityTest {

    /** Запускає JavaFX toolkit перед тестуванням порядку графічних шарів. */
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

    /** Перевіряє, що меню переміщується на передній план і блокує редакторський вміст. */
    @Test
    void mainMenuMovesAboveLaterOverlaysAndDisablesEditorContent() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainView mainView = new MainView(
                    (NeuronType ignored) -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    100.0,
                    localization
            );
            mainView.createScene(900.0, 600.0);
            Pane laterOverlay = new Pane();
            mainView.node().getChildren().add(laterOverlay);

            MainMenuView menu = mainView.mainMenu();
            menu.setActions(new MainMenuView.Actions(
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    (project, name) -> project,
                    ignored -> { },
                    () -> { }
            ));

            menu.showMenu();

            assertSame(menu, mainView.node().getChildren().getLast(),
                    "Головне меню має бути над усіма накладками, доданими раніше");
            assertTrue(mainView.node().getChildren().get(0).isDisable(),
                    "Під час відкритого меню вміст редактора має бути недоступним");

            menu.hide();
            assertFalse(mainView.node().getChildren().get(0).isDisable(),
                    "Після закриття меню доступність редактора має відновитися");
            mainView.dispose();
        });
    }

    /** Виконує UI-перевірку в потоці JavaFX і повертає помилки до JUnit. */
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
            throw new AssertionError("UI-тест завершився помилкою", failure[0]);
        }
    }
}
