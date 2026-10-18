package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.function.Consumer;

/** Координує життєвий цикл накладки налаштувань нейрона та її взаємодію з головним меню. */
public final class NeuronSettingsOverlayCoordinator {

    private final WorkspaceView workspace;
    private final LocalizationService localization;
    private final ChangeListener<Boolean> settingsVisibilityListener =
            (observable, wasVisible, isVisible) -> {
                if (Boolean.FALSE.equals(isVisible)) {
                    stopWatchingMainMenu();
                }
            };

    private NeuronSettingsView settingsView;
    private MainMenuView watchedMainMenu;
    private ChangeListener<Boolean> mainMenuVisibilityListener;

    /**
     * Створює координатор, який використовує наявну робочу область і службу локалізації.
     *
     * @param workspace робоча область редактора, до якої належить сцена.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо робочу область або службу локалізації не задано.
     */
    public NeuronSettingsOverlayCoordinator(WorkspaceView workspace, LocalizationService localization) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Показує накладку налаштувань нейрона, закриваючи її під час відкриття головного меню.
     *
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених параметрів.
     * @throws NullPointerException якщо нейрон або callback збереження не задано.
     * @throws IllegalStateException якщо робоча область не належить контейнеру накладок.
     */
    public void show(Neuron neuron, Consumer<NeuronParameters> onSave) {
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        Scene scene = workspace.node().getScene();
        if (scene == null) {
            return;
        }

        StackPane overlayHost = resolveOverlayHost(workspace.node());
        hide();
        if (settingsView == null) {
            settingsView = new NeuronSettingsView(localization);
            settingsView.visibleProperty().addListener(settingsVisibilityListener);
        }
        watchMainMenuVisibility(overlayHost);
        settingsView.showIn(overlayHost, neuron, onSave);
    }

    /** Приховує накладку без збереження введених параметрів і завершує тимчасове спостереження меню. */
    public void hide() {
        if (settingsView != null) {
            settingsView.dispose();
        }
        stopWatchingMainMenu();
    }

    /**
     * Знаходить контейнер, що безпосередньо містить головне меню, замість довільної зовнішньої обгортки.
     * Якщо меню відсутнє, використовує найзовнішній StackPane для ізольованих або вбудованих сценаріїв.
     *
     * @param editorContent вузол робочої області, що належить головній сцені.
     * @return контейнер накладки, який містить головне меню, або резервний StackPane.
     * @throws NullPointerException якщо вузол робочої області не задано.
     * @throws IllegalStateException якщо в ланцюжку предків немає StackPane-контейнера.
     */
    public static StackPane resolveOverlayHost(Node editorContent) {
        Objects.requireNonNull(editorContent, "editorContent");
        StackPane fallback = null;
        Parent ancestor = editorContent.getParent();
        while (ancestor != null) {
            if (ancestor instanceof StackPane stackPane) {
                fallback = stackPane;
                boolean containsMainMenu = stackPane.getChildren().stream()
                        .anyMatch(MainMenuView.class::isInstance);
                if (containsMainMenu) {
                    return stackPane;
                }
            }
            ancestor = ancestor.getParent();
        }
        if (fallback == null) {
            throw new IllegalStateException(
                    "Не вдалося знайти контейнер StackPane для внутрішньої накладки налаштувань нейрона");
        }
        return fallback;
    }

    /** Підписується на головне меню, щоб воно не залишалося під відкритою модальною накладкою. */
    private void watchMainMenuVisibility(StackPane overlayHost) {
        MainMenuView mainMenu = overlayHost.getChildren().stream()
                .filter(MainMenuView.class::isInstance)
                .map(MainMenuView.class::cast)
                .findFirst()
                .orElse(null);
        if (mainMenu == null || (watchedMainMenu == mainMenu && mainMenuVisibilityListener != null)) {
            return;
        }
        stopWatchingMainMenu();
        watchedMainMenu = mainMenu;
        mainMenuVisibilityListener = (observable, wasVisible, isVisible) -> {
            if (Boolean.TRUE.equals(isVisible)) {
                hide();
            }
        };
        mainMenu.visibleProperty().addListener(mainMenuVisibilityListener);
    }

    /** Знімає слухач головного меню, завершуючи тимчасову координацію модального стану. */
    private void stopWatchingMainMenu() {
        if (watchedMainMenu != null && mainMenuVisibilityListener != null) {
            watchedMainMenu.visibleProperty().removeListener(mainMenuVisibilityListener);
        }
        watchedMainMenu = null;
        mainMenuVisibilityListener = null;
    }
}
