package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що життєвим циклом повідомлень керує view-шар і що резервний рядок стану оновлюється коректно.
 */
final class StatusMessagePresenterTest {

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

    @Test
    void rejectsNullFallbackHandler() {
        assertThrows(IllegalArgumentException.class, () -> new StatusMessagePresenter(null));
    }

    @Test
    void presenterFindsStatusLabelAndReleasesLifecycle() throws Exception {
        runOnFxThread(() -> {
            Label label = new Label();
            label.getStyleClass().add("status-label");
            VBox root = new VBox(label);
            root.getStyleClass().add("status-bar");
            Scene scene = new Scene(root, 300.0, 100.0);
            AtomicReference<String> fallbackText = new AtomicReference<>();
            StatusMessagePresenter presenter = new StatusMessagePresenter(message -> {
                fallbackText.set(message);
                label.setText(message);
            });

            presenter.attach(scene);
            presenter.show("Зміни збережено");

            assertEquals("Зміни збережено", fallbackText.get());
            assertEquals("Зміни збережено", label.getText());
            assertEquals(1.0, label.getOpacity(), 0.0001);
            assertFalse(root.isMouseTransparent());

            presenter.dispose();

            assertEquals(0.0, label.getOpacity(), 0.0001);
            assertTrue(root.isMouseTransparent());
            assertNull(root.getOnMouseEntered());
            assertNull(root.getOnMouseExited());
        });
    }

    @Test
    void mainViewOwnsStatusMessageAttachmentAndCleanup() throws Exception {
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
            Scene scene = view.createScene(800.0, 600.0);

            view.setStatus("Редактор готовий");

            StatusMessageTargetLocator.Target target = StatusMessageTargetLocator
                    .findByText(scene, "Редактор готовий")
                    .orElseThrow();
            assertNotNull(target.label());
            assertEquals(1.0, target.label().getOpacity(), 0.0001);

            view.dispose();

            assertEquals(0.0, target.label().getOpacity(), 0.0001);
        });
    }

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
