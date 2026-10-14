package com.example.neuronmap.view.status;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.editor.MainView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє прив’язку життєвого циклу повідомлень до власного рядка стану та звільнення його ресурсів.
 */
final class StatusMessagePresenterTest {

    /**
     * Запускає JavaFX один раз перед тестами, які створюють графічні вузли.
     *
     * @throws Exception якщо середовище JavaFX не запустилося вчасно.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Переконується, що для створення презентера обов’язково потрібне конкретне подання рядка стану.
     */
    @Test
    void rejectsNullStatusBar() {
        assertThrows(IllegalArgumentException.class, () -> new StatusMessagePresenter(null));
    }

    /**
     * Перевіряє, що мітка з таким самим текстом у сцені не перехоплює життєвий цикл повідомлення.
     *
     * @throws Exception якщо виконання на потоці JavaFX завершується помилкою.
     */
    @Test
    void presenterUsesItsStatusBarInsteadOfSearchingTheScene() throws Exception {
        runOnFxThread(() -> {
            StatusBarView statusBar = new StatusBarView();
            Pane statusBarNode = statusBar.node();
            Label actualStatusLabel = statusBar.messageLabel();
            Label unrelatedLabel = new Label("Зміни збережено");
            VBox root = new VBox(statusBarNode, unrelatedLabel);
            Scene scene = new Scene(root, 300.0, 100.0);
            StatusMessagePresenter presenter = new StatusMessagePresenter(statusBar);
            presenter.show("Зміни збережено");

            assertEquals("Зміни збережено", actualStatusLabel.getText());
            assertEquals("Зміни збережено", unrelatedLabel.getText());
            assertEquals(1.0, actualStatusLabel.getOpacity(), 0.0001);
            assertEquals(1.0, unrelatedLabel.getOpacity(), 0.0001);
            assertFalse(statusBar.node().isMouseTransparent());
            assertTrue(statusBar.node().isPickOnBounds());
            assertNotNull(statusBarNode.getOnMouseEntered());
            assertNotNull(statusBarNode.getOnMouseExited());
            assertNull(unrelatedLabel.getOnMouseEntered());
            assertNull(unrelatedLabel.getOnMouseExited());

            presenter.dispose();

            assertEquals(0.0, actualStatusLabel.getOpacity(), 0.0001);
            assertTrue(statusBar.node().isMouseTransparent());
            assertNull(statusBarNode.getOnMouseEntered());
            assertNull(statusBarNode.getOnMouseExited());
            presenter.dispose();
        });
    }

    /**
     * Перевіряє передавання повідомлень з MainView до вбудованого StatusBarView і завершення його життєвого циклу.
     *
     * @throws Exception якщо виконання на потоці JavaFX завершується помилкою.
     */
    @Test
    void mainViewUsesItsComposedStatusBarAndDisposesIt() throws Exception {
        runOnFxThread(() -> {
            MainView view = new MainView(
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    100.0,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );
            view.createScene(800.0, 600.0);

            Pane content = (Pane) view.node().getChildren().get(0);
            Pane statusBarNode = (Pane) content.getChildren().get(2);
            Label statusLabel = (Label) statusBarNode.getChildren().get(0);

            view.setStatus("Редактор готовий");

            assertEquals("Редактор готовий", statusLabel.getText());
            assertEquals(1.0, statusLabel.getOpacity(), 0.0001);
            assertNotNull(statusBarNode.getOnMouseEntered());

            view.dispose();

            assertEquals(0.0, statusLabel.getOpacity(), 0.0001);
            assertNull(statusBarNode.getOnMouseEntered());
            assertNull(statusBarNode.getOnMouseExited());
        });
    }

    /**
     * Перевіряє, що порожнє повідомлення одразу прибирає мітку й не залишає активну область наведення.
     *
     * @throws Exception якщо виконання на потоці JavaFX завершується помилкою.
     */
    @Test
    void blankMessageClearsStatusBarWithoutStartingItsVisibleTimer() throws Exception {
        runOnFxThread(() -> {
            StatusBarView statusBar = new StatusBarView();
            StatusMessagePresenter presenter = new StatusMessagePresenter(statusBar);

            presenter.show("Повідомлення");
            presenter.show("  ");

            assertFalse(statusBar.messageLabel().isVisible());
            assertEquals("", statusBar.messageLabel().getText());
            assertEquals(0.0, statusBar.messageLabel().getOpacity(), 0.0001);
            assertTrue(statusBar.node().isMouseTransparent());

            presenter.dispose();
        });
    }

    /**
     * Виконує перевірку на потоці JavaFX і передає помилку назад у тестовий потік.
     *
     * @param action перевірка, яка має виконатися на потоці JavaFX.
     * @throws Exception якщо перевірка не завершується вчасно або завершується помилкою.
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }
}
