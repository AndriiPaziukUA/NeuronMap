package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє звільнення реєстрацій, callback-ів і таблиць стилів головного подання редактора. */
final class MainViewToolbarLifecycleTest {

    /** Перевіряє, що dispose головного подання від'єднує toolbar і не допускає подальших callback-ів. */
    @Test
    void disposingMainViewDetachesToolbarCallbacksAndLocalizationListener() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            AtomicInteger addNeuronCalls = new AtomicInteger();
            MainView view = new MainView(
                    (NeuronType ignored) -> addNeuronCalls.incrementAndGet(),
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    650.0,
                    localization
            );
            String initialButtonText = view.toolbar().addExcitatoryButton().getText();

            view.toolbar().addExcitatoryButton().fire();
            assertEquals(1, addNeuronCalls.get());

            view.dispose();
            view.dispose();
            view.toolbar().addExcitatoryButton().fire();
            localization.previewLanguage(SupportedLanguage.UKRAINIAN);

            assertEquals(1, addNeuronCalls.get());
            assertEquals(initialButtonText, view.toolbar().addExcitatoryButton().getText());
            view.mainMenu().dispose();
        });
    }

    /** Перевіряє, що dispose прибирає власний CSS і від'єднує слухачі розміру робочої області. */
    @Test
    void disposingMainViewReleasesOwnedStylesheetAndWorkspaceResizeListeners() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainView view = new MainView(
                    ignored -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    () -> { },
                    ignored -> { },
                    650.0,
                    localization
            );
            Scene scene = view.createScene(640.0, 480.0);
            URL appStylesheet = MainView.class.getResource("/app.css");
            URL externalStylesheet = MainView.class.getResource("/toolbar.css");
            assertNotNull(appStylesheet);
            assertNotNull(externalStylesheet);
            String appStylesheetUrl = appStylesheet.toExternalForm();
            String externalStylesheetUrl = externalStylesheet.toExternalForm();
            scene.getStylesheets().add(externalStylesheetUrl);
            assertTrue(scene.getStylesheets().contains(appStylesheetUrl));
            assertThrows(IllegalStateException.class, () -> view.createScene(800.0, 600.0),
                    "Повторне створення сцени не повинно порушувати володіння першою сценою");
            assertTrue(scene.getStylesheets().contains(appStylesheetUrl),
                    "Невдала повторна спроба не повинна прибирати CSS із чинної сцени");

            view.workspace().node().resize(320.0, 240.0);
            Canvas gridCanvas = (Canvas) view.workspace().node().getChildren().get(0);
            assertEquals(320.0, gridCanvas.getWidth());
            assertEquals(240.0, gridCanvas.getHeight());

            view.dispose();
            assertFalse(scene.getStylesheets().contains(appStylesheetUrl));
            assertTrue(scene.getStylesheets().contains(externalStylesheetUrl),
                    "Dispose не має прибирати таблиці стилів, якими володіє інший компонент");

            view.workspace().node().resize(480.0, 360.0);
            assertEquals(320.0, gridCanvas.getWidth(),
                    "Після dispose зміна розміру не повинна запускати перемальовування сітки");
            assertEquals(240.0, gridCanvas.getHeight(),
                    "Після dispose полотно сітки має зберігати останній намальований розмір");
            view.mainMenu().dispose();
        });
    }
}
