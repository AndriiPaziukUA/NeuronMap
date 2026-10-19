package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.window.WindowChromeView;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє звільнення ресурсів головного подання та спільну локалізацію його компонентів. */
final class MainViewToolbarLifecycleTest {

    /** Перевіряє, що dispose головного подання від'єднує toolbar і не допускає подальших callback-ів.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
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

    /** Перевіряє, що dispose прибирає власний CSS і від'єднує слухачі розміру робочої області.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
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

    /** Перевіряє спільний preview і rollback мови для панелі, меню та власного заголовка вікна.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void sharedLocalizationPreviewAndRollbackUpdatesToolbarMenuAndWindow() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            MainView view = new MainView(
                    ignored -> { }, () -> { }, () -> { }, () -> { }, () -> { }, ignored -> { },
                    650.0, localization);
            Stage stage = new Stage();
            Scene windowScene = new Scene(new StackPane(), 800.0, 600.0);
            stage.setScene(windowScene);
            WindowChromeView windowChrome = new WindowChromeView(stage, localization);
            boolean menuDisposed = false;

            try {
                view.createScene(640.0, 480.0);
                windowChrome.installInto(windowScene);

                URL menuStylesheet = MainMenuView.class.getResource("/menu-enhancements.css");
                URL windowStylesheet = WindowChromeView.class.getResource("/window-chrome.css");
                assertNotNull(menuStylesheet);
                assertNotNull(windowStylesheet);
                assertTrue(view.mainMenu().getStylesheets().contains(menuStylesheet.toExternalForm()),
                        "Меню має підключати власну таблицю стилів");
                assertTrue(windowScene.getStylesheets().contains(windowStylesheet.toExternalForm()),
                        "Заголовок вікна має додавати власну таблицю стилів до сцени");

                Button toolbarButton = view.toolbar().addExcitatoryButton();
                Button minimizeButton = (Button) windowScene.getRoot().lookup(".window-control-minimize");
                assertNotNull(minimizeButton, "Заголовок має містити кнопку згортання");

                String originalToolbarText = toolbarButton.getText();
                List<String> originalMenuTexts = menuButtonTexts(view.mainMenu());
                String originalWindowText = minimizeButton.getAccessibleText();
                assertFalse(originalMenuTexts.isEmpty(), "Головна сторінка меню має містити кнопки");

                localization.previewLanguage(SupportedLanguage.UKRAINIAN);

                assertNotEquals(originalToolbarText, toolbarButton.getText());
                assertNotEquals(originalMenuTexts, menuButtonTexts(view.mainMenu()));
                assertNotEquals(originalWindowText, minimizeButton.getAccessibleText());

                localization.discardLanguagePreview();

                assertEquals(originalToolbarText, toolbarButton.getText());
                assertEquals(originalMenuTexts, menuButtonTexts(view.mainMenu()));
                assertEquals(originalWindowText, minimizeButton.getAccessibleText());

                view.dispose();
                view.mainMenu().dispose();
                menuDisposed = true;
                windowChrome.dispose();
                localization.previewLanguage(SupportedLanguage.RUSSIAN);

                assertEquals(originalToolbarText, toolbarButton.getText(),
                        "Звільнена панель не повинна реагувати на зміни локалі");
                assertEquals(originalMenuTexts, menuButtonTexts(view.mainMenu()),
                        "Звільнене меню не повинно реагувати на зміни локалі");
                assertEquals(originalWindowText, minimizeButton.getAccessibleText(),
                        "Звільнений заголовок не повинен реагувати на зміни локалі");
                localization.discardLanguagePreview();
            } finally {
                windowChrome.dispose();
                view.dispose();
                if (!menuDisposed) {
                    view.mainMenu().dispose();
                }
                stage.close();
            }
        });
    }

    /** Повертає впорядковані підписи кнопок меню для стабільного порівняння між локалями.
     *
     * @param menu подання меню, кнопки якого потрібно перевірити.
     * @return відсортований список поточних підписів кнопок.
     */
    private static List<String> menuButtonTexts(MainMenuView menu) {
        return menu.lookupAll(".main-menu-button").stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .map(Button::getText)
                .sorted()
                .toList();
    }
}
