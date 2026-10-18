package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.function.Consumer;

/** Координує життєвий цикл модальної накладки налаштувань та її взаємодію з головним меню. */
public final class NeuronSettingsOverlayCoordinator {

    private final StackPane overlayHost;
    private final MainMenuView mainMenu;
    private final WorkspaceView legacyWorkspace;
    private final LocalizationService localization;
    private final ChangeListener<Boolean> settingsVisibilityListener =
            (observable, wasVisible, isVisible) -> {
                if (Boolean.FALSE.equals(isVisible)) {
                    stopWatchingMainMenu();
                }
            };

    private NeuronSettingsView settingsView;
    private ChangeListener<Boolean> mainMenuVisibilityListener;
    private boolean disposed;

    /**
     * Створює координатор із явно переданими власником інтерфейсу контейнером і головним меню.
     * Координатор не обходить дерево JavaFX і не залежить від внутрішньої структури батьківських вузлів.
     *
     * @param overlayHost контейнер, до якого додаються модальні накладки редактора.
     * @param mainMenu подання головного меню, видимість якого визначає пріоритет модальної накладки.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     */
    public NeuronSettingsOverlayCoordinator(
            StackPane overlayHost,
            MainMenuView mainMenu,
            LocalizationService localization
    ) {
        this.overlayHost = Objects.requireNonNull(overlayHost, "overlayHost");
        this.mainMenu = Objects.requireNonNull(mainMenu, "mainMenu");
        this.legacyWorkspace = null;
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Сумісний конструктор для наявних ізольованих компонентів та старих тестових фікстур.
     * Новий код застосунку має передавати контейнер і головне меню явно через основний конструктор.
     *
     * @param workspace робоча область редактора.
     * @param localization служба локалізації.
     * @deprecated Використовуйте конструктор з явними {@link StackPane} та {@link MainMenuView}.
     */
    @Deprecated
    public NeuronSettingsOverlayCoordinator(WorkspaceView workspace, LocalizationService localization) {
        this.overlayHost = null;
        this.mainMenu = null;
        this.legacyWorkspace = Objects.requireNonNull(workspace, "workspace");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Показує накладку налаштувань нейрона, закриваючи її під час відкриття головного меню.
     *
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених параметрів.
     * @throws NullPointerException якщо нейрон або callback збереження не задано.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void show(Neuron neuron, Consumer<NeuronParameters> onSave) {
        ensureNotDisposed();
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        StackPane activeOverlayHost = overlayHost;
        MainMenuView activeMainMenu = mainMenu;
        if (legacyWorkspace != null) {
            activeOverlayHost = resolveLegacyOverlayHost(legacyWorkspace.node());
            activeMainMenu = findMainMenu(activeOverlayHost);
        }
        Scene scene = activeOverlayHost.getScene();
        if (scene == null || (activeMainMenu != null && activeMainMenu.isMenuVisible())) {
            hide();
            return;
        }

        hide();
        if (settingsView == null) {
            settingsView = new NeuronSettingsView(localization);
            settingsView.visibleProperty().addListener(settingsVisibilityListener);
        }
        watchMainMenuVisibility(activeMainMenu);
        settingsView.showIn(activeOverlayHost, neuron, onSave);
    }

    /** Приховує накладку без збереження введених параметрів і завершує тимчасове спостереження меню. */
    public void hide() {
        if (settingsView != null) {
            settingsView.hide();
        }
        stopWatchingMainMenu();
    }

    /** Остаточно звільняє подання й слухачі координатора; повторний виклик безпечний. */
    public void dispose() {
        if (disposed) {
            return;
        }
        stopWatchingMainMenu();
        if (settingsView != null) {
            settingsView.visibleProperty().removeListener(settingsVisibilityListener);
            settingsView.dispose();
            settingsView = null;
        }
        disposed = true;
    }

    /**
     * Перевіряє, чи координатор ще може показувати накладку.
     *
     * @throws IllegalStateException якщо координатор остаточно звільнено.
     */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron settings overlay coordinator is disposed");
        }
    }

    /** Підписується на передане меню; старий сумісний конструктор може працювати без нього. */
    private void watchMainMenuVisibility(MainMenuView activeMainMenu) {
        if (activeMainMenu == null || mainMenuVisibilityListener != null) {
            return;
        }
        watchedMainMenu = activeMainMenu;
        mainMenuVisibilityListener = (observable, wasVisible, isVisible) -> {
            if (Boolean.TRUE.equals(isVisible)) {
                hide();
            }
        };
        activeMainMenu.visibleProperty().addListener(mainMenuVisibilityListener);
    }

    private MainMenuView findMainMenu(StackPane host) {
        return host.getChildren().stream()
                .filter(MainMenuView.class::isInstance)
                .map(MainMenuView.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** Резервний пошук лише для сумісного старого конструктора. */
    private static StackPane resolveLegacyOverlayHost(Node editorContent) {
        Parent ancestor = editorContent.getParent();
        StackPane fallback = null;
        while (ancestor != null) {
            if (ancestor instanceof StackPane stackPane) {
                fallback = stackPane;
                if (stackPane.getChildren().stream().anyMatch(MainMenuView.class::isInstance)) {
                    return stackPane;
                }
            }
            ancestor = ancestor.getParent();
        }
        if (fallback == null) {
            throw new IllegalStateException("Не вдалося знайти контейнер StackPane для накладки налаштувань");
        }
        return fallback;
    }

    private MainMenuView watchedMainMenu;

    /** Знімає слухач головного меню, завершуючи тимчасову координацію модального стану. */
    private void stopWatchingMainMenu() {
        if (watchedMainMenu != null && mainMenuVisibilityListener != null) {
            watchedMainMenu.visibleProperty().removeListener(mainMenuVisibilityListener);
        }
        watchedMainMenu = null;
        mainMenuVisibilityListener = null;
    }
}
