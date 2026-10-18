package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.function.Consumer;

/** Координує життєвий цикл модальної накладки налаштувань та її взаємодію з головним меню. */
public final class NeuronSettingsOverlayCoordinator {

    private final StackPane overlayHost;
    private final MainMenuView mainMenu;
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
     * Створює координатор з явними залежностями контейнера накладок і головного меню.
     * Координатор не шукає елементи в дереві JavaFX та не залежить від структури предків.
     *
     * @param overlayHost контейнер, до якого додаються модальні накладки редактора.
     * @param mainMenu подання головного меню, видимість якого визначає пріоритет накладки.
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
        Scene scene = overlayHost.getScene();
        if (scene == null || mainMenu.isMenuVisible()) {
            hide();
            return;
        }

        hide();
        if (settingsView == null) {
            settingsView = new NeuronSettingsView(localization);
            settingsView.visibleProperty().addListener(settingsVisibilityListener);
        }
        watchMainMenuVisibility();
        settingsView.showIn(overlayHost, neuron, onSave);
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

    /** Перевіряє, чи координатор ще може показувати накладку. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron settings overlay coordinator is disposed");
        }
    }

    /** Підписується на головне меню лише на час показу накладки. */
    private void watchMainMenuVisibility() {
        if (mainMenuVisibilityListener != null) {
            return;
        }
        mainMenuVisibilityListener = (observable, wasVisible, isVisible) -> {
            if (Boolean.TRUE.equals(isVisible)) {
                hide();
            }
        };
        mainMenu.visibleProperty().addListener(mainMenuVisibilityListener);
    }

    /** Знімає слухач головного меню, завершуючи тимчасову координацію модального стану. */
    private void stopWatchingMainMenu() {
        if (mainMenuVisibilityListener != null) {
            mainMenu.visibleProperty().removeListener(mainMenuVisibilityListener);
        }
        mainMenuVisibilityListener = null;
    }
}
