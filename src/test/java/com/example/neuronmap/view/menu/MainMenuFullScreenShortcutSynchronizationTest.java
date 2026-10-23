package com.example.neuronmap.view.menu;

import com.example.neuronmap.controller.MainMenuController;
import com.example.neuronmap.controller.FullScreenShortcutEvent;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.settings.GlobalSettingsFullScreenPreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.settings.SettingsEditSession;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє синхронізацію перемикача повноекранного режиму з F11 і ізоляцію змін інших налаштувань. */
final class MainMenuFullScreenShortcutSynchronizationTest {

    /** Запускає JavaFX toolkit для перевірки подій меню на потоці інтерфейсу. */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє, що F11 одразу оновлює перемикач і прибирає незбережений стан лише цього параметра. */
    @Test
    void shortcutRefreshesToggleAndAcceptsPersistedFullScreenValue(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture(directory);
            fixture.controller().open();
            fixture.view().homePageForTest().settingsButtonForTest().fire();

            fixture.fullScreenValue().set(true);
            fixture.preferences().saveFullScreenEnabled(true);
            Event.fireEvent(fixture.scene(), new FullScreenShortcutEvent(true));

            assertTrue(fixture.view().fullScreenSettingsPageForTest().toggleForTest().isSelected(),
                    "Перемикач має відобразити зміну навіть на вже відкритій сторінці");
            assertTrue(fixture.preferences().loadFullScreenEnabled());
            assertEquals(0, fixture.fullScreenPersistCalls().get(),
                    "Синхронізація не повинна повторно зберігати значення, уже записане власником вікна");
            assertFalse(fixture.view().isSettingsDirty(),
                    "Збережений через F11 параметр не має залишатися незбереженою зміною сеансу");
            assertFalse(fixture.view().saveSettingsButtonForTest().isVisible());

            fixture.view().backButtonForTest().fire();
            assertFalse(fixture.view().isConfirmingUnsavedChanges(),
                    "Перехід не повинен вимагати підтвердження лише через використання F11");
            assertFalse(fixture.view().isSettingsPage());
            fixture.controller().dispose();
        });
    }

    /** Перевіряє, що F11 не приховує незбережені зміни інших параметрів у тій самій сесії. */
    @Test
    void shortcutKeepsOtherSettingChangesPending(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture(directory);
            AtomicReference<String> theme = new AtomicReference<>("light");
            SettingsEditSession.Setting<String> themeSetting = fixture.controller().registerSetting(
                    theme::get, theme::set, ignored -> { });

            fixture.controller().open();
            fixture.view().homePageForTest().settingsButtonForTest().fire();
            fixture.controller().previewSetting(themeSetting, "dark");

            fixture.fullScreenValue().set(true);
            fixture.preferences().saveFullScreenEnabled(true);
            Event.fireEvent(fixture.scene(), new FullScreenShortcutEvent(true));

            assertTrue(fixture.view().fullScreenSettingsPageForTest().toggleForTest().isSelected());
            assertTrue(fixture.view().isSettingsDirty(),
                    "Зміна теми має залишатися незбереженою після синхронізації повноекранного режиму");
            assertTrue(fixture.view().saveSettingsButtonForTest().isVisible());
            assertEquals("dark", theme.get());

            fixture.view().backButtonForTest().fire();
            assertTrue(fixture.view().isConfirmingUnsavedChanges(),
                    "Для інших незбережених параметрів підтвердження лишається обов'язковим");
            fixture.view().discardChangesButtonForTest().fire();
            assertEquals("light", theme.get(), "Відкат має відновлювати інше налаштування");
            assertTrue(fixture.fullScreenValue().get(),
                    "Відкат інших параметрів не повинен вимикати вже збережений F11 режим");
            assertTrue(fixture.preferences().loadFullScreenEnabled());
            fixture.controller().dispose();
        });
    }

    /** Перевіряє, що подальша ручна зміна повзунка знову позначається незбереженою. */
    @Test
    void manualToggleAfterShortcutStillRequiresConfirmation(@TempDir Path directory) throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture(directory);
            fixture.controller().open();
            fixture.view().homePageForTest().settingsButtonForTest().fire();

            fixture.fullScreenValue().set(true);
            fixture.preferences().saveFullScreenEnabled(true);
            Event.fireEvent(fixture.scene(), new FullScreenShortcutEvent(true));
            fixture.view().fullScreenSettingsPageForTest().toggleForTest().fire();

            assertFalse(fixture.fullScreenValue().get());
            assertTrue(fixture.view().isSettingsDirty(),
                    "Ручна зміна відносно останнього збереженого стану має бути незбереженою");
            fixture.view().backButtonForTest().fire();
            assertTrue(fixture.view().isConfirmingUnsavedChanges());
            fixture.view().discardChangesButtonForTest().fire();
            assertTrue(fixture.fullScreenValue().get(), "Відкат ручного preview має відновити збережений стан");
            assertTrue(fixture.preferences().loadFullScreenEnabled());
            fixture.controller().dispose();
        });
    }

    /** Створює ізольовану сцену меню та тестове сховище повноекранного параметра. */
    private Fixture createFixture(Path directory) {
        GlobalSettingsFullScreenPreferenceStore preferences = new GlobalSettingsFullScreenPreferenceStore(
                new GlobalSettingsStore(directory.resolve("global.properties")));
        AtomicBoolean fullScreenValue = new AtomicBoolean(false);
        AtomicInteger persistCalls = new AtomicInteger();
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        MainMenuView view = new MainMenuView(localization, ignored -> { });
        MainMenuController controller = new MainMenuController(
                view, localization, List::of, () -> { }, ignored -> { },
                (project, name) -> project, ignored -> { }, () -> { }, () -> { }, () -> { });
        controller.registerFullScreenSetting(fullScreenValue::get, fullScreenValue::set,
                ignored -> persistCalls.incrementAndGet());
        Scene scene = new Scene(new StackPane(view));
        controller.install(scene);
        return new Fixture(view, controller, scene, fullScreenValue, preferences, persistCalls);
    }

    /** Зберігає залежності одного тестового сценарію, щоб очищення контролера було явним. */
    private record Fixture(
            MainMenuView view,
            MainMenuController controller,
            Scene scene,
            AtomicBoolean fullScreenValue,
            GlobalSettingsFullScreenPreferenceStore preferences,
            AtomicInteger fullScreenPersistCalls
    ) { }

    /** Виконує перевірку у JavaFX Application Thread і передає винятки потоку JUnit. */
    private static void runOnFxThread(Runnable action) throws InterruptedException {
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
            throw new AssertionError("Перевірка синхронізації повноекранного режиму завершилася помилкою", failure[0]);
        }
    }
}
