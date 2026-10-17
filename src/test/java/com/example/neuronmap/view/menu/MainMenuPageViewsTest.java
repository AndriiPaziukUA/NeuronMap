package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Перевіряє локалізовані написи, callback-и кнопок і звільнення обробників головної сторінки. */
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
            ), view.buttonTextsForTest());

            List<Button> buttons = view.getChildren().stream()
                    .map(Button.class::cast)
                    .toList();
            List<String> expectedActions = List.of(
                    "continue", "new-project", "load", "settings", "exit"
            );
            assertEquals(expectedActions.size(), buttons.size());
            for (int index = 0; index < buttons.size(); index++) {
                buttons.get(index).fire();
                assertEquals(expectedActions.get(index), invoked.get());
            }

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

    /**
     * Перевіряє підготовку геометрії списку перед першим і повторним показом без відкриття popup-вікна.
     *
     * <p>Тест надсилає події {@code onShowing} безпосередньо обробнику комбінованого списку.
     * Це перевіряє синхронне компонування сцени перед позиціюванням popup, не створюючи
     * видимого {@code Stage} або окремого системного вікна під час виконання тестів.</p>
     */
    @Test
    void languagePopupGeometryIsPreparedOnFirstAndRepeatedDisplayWithoutShowingWindow() throws Exception {
        runOnFxThread(() -> {
            MainMenuView view = new MainMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")), ignored -> { });
            view.setActions(new MainMenuView.Actions(
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { },
                    ignored -> { }, (project, name) -> project, ignored -> { }, () -> { }
            ));
            view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            StackPane root = new StackPane(view);
            Scene scene = new Scene(root, 1000.0, 700.0);
            root.resize(1000.0, 700.0);
            root.applyCss();
            root.layout();

            ComboBox<?> selector = view.languageSelectorForTest();
            try {
                view.showMainPage();
                view.showMenu();
                view.showSettingsPage();
                assertPopupGeometryPrepared(selector, scene);

                view.hide();
                view.showMainPage();
                view.showMenu();
                view.showSettingsPage();
                assertPopupGeometryPrepared(selector, scene);
            } finally {
                view.hide();
                view.dispose();
            }
        });
    }

    /**
     * Викликає лише обробник передпоказу та перевіряє результат компонування сцени без popup-вікна.
     *
     * @param selector комбінований список, для якого готується геометрія.
     * @param scene сцена, що містить меню.
     */
    private static void assertPopupGeometryPrepared(ComboBox<?> selector, Scene scene) {
        assertEquals(scene, selector.getScene(), "Список мов має бути приєднаний до тестової сцени");
        assertNotNull(selector.getOnShowing(), "Перед показом має бути зареєстрований обробник компонування");
        assertFalse(selector.isShowing(), "Тест не повинен відкривати справжнє popup-вікно");

        selector.getOnShowing().handle(
                new Event(selector, selector, ComboBoxBase.ON_SHOWING));

        assertTrue(selector.getWidth() > 0.0, "ComboBox має отримати розраховану ширину перед показом");
        assertTrue(selector.getHeight() > 0.0, "ComboBox має отримати розраховану висоту перед показом");
        assertNotNull(selector.localToScene(0.0, selector.getHeight()),
                "Після компонування ComboBox має мати координати в межах сцени");
        assertFalse(selector.isShowing(), "Виклик обробника не повинен показувати popup-вікно");
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
