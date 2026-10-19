package com.example.neuronmap.view.editor;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.status.StatusBarView;
import com.example.neuronmap.view.status.StatusMessagePresenter;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.function.Consumer;

/** Збирає робочу область редактора, панель інструментів, рядок стану та накладку головного меню. */
public final class MainView {

    private final StackPane root = new StackPane();
    private final RootPane content = new RootPane();
    private final ToolbarView toolbar;
    private final WorkspaceView workspace = new WorkspaceView();
    private final StatusBarView statusBar = new StatusBarView();
    private final StatusMessagePresenter statusMessagePresenter = new StatusMessagePresenter(statusBar);
    private final MainMenuView mainMenu;
    private boolean normalizingRootLayerOrder;
    private boolean disposed;
    private Scene createdScene;
    private String ownedStylesheetUrl;
    private final ListChangeListener<Node> rootLayerOrderListener = ignored -> keepMenuAboveOverlays();

    /** Створює подання редактора з переданою службою локалізації. */
    public MainView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis,
            LocalizationService localization
    ) {
        toolbar = new ToolbarView(addNeuron, group, ungroup, pauseResume, stopSignals,
                speedChanged, initialSpeedMillis, localization);
        mainMenu = new MainMenuView(localization, this::setMainMenuVisualState);
        content.getStyleClass().add("root");
        content.getChildren().addAll(toolbar.node(), workspace.node(), statusBar.node());
        content.setPickOnBounds(true);
        content.requestLayout();
        root.getStyleClass().add("root");
        root.getChildren().addAll(content, mainMenu);
        root.setPickOnBounds(true);
        root.getChildren().addListener(rootLayerOrderListener);
    }

    /** Зберігає сумісність зі старими викликами конструктора, що передавали callback вилученої кнопки. */
    public MainView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable ignoredExitDeleteConnectionMode,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis,
            LocalizationService localization
    ) {
        this(addNeuron, group, ungroup, pauseResume, stopSignals, speedChanged, initialSpeedMillis, localization);
    }

    /**
     * Створює єдину сцену JavaFX та підключає основну таблицю стилів, залишаючись її власником до {@link #dispose()}.
     *
     * @param width початкова ширина сцени.
     * @param height початкова висота сцени.
     * @return створена сцена редактора.
     * @throws IllegalStateException якщо подання звільнено, сцена вже створена або ресурс {@code app.css} відсутній.
     */
    public Scene createScene(double width, double height) {
        if (disposed) {
            throw new IllegalStateException("Main view is disposed");
        }
        if (createdScene != null) {
            throw new IllegalStateException("Main view scene has already been created");
        }

        URL css = getClass().getResource("/app.css");
        if (css == null) {
            throw new IllegalStateException("app.css is missing");
        }
        Scene scene = new Scene(root, width, height);
        String stylesheetUrl = css.toExternalForm();
        if (!scene.getStylesheets().contains(stylesheetUrl)) {
            scene.getStylesheets().add(stylesheetUrl);
            ownedStylesheetUrl = stylesheetUrl;
        }
        createdScene = scene;
        root.layout();
        return scene;
    }

    /** Призупиняє таймери й анімацію повідомлення рядка стану, не змінюючи його видимий текст. */
    public void pauseStatusMessageLifecycle() {
        statusMessagePresenter.pause();
    }

    /** Відновлює життєвий цикл повідомлення рядка стану після зняття паузи редактора. */
    public void resumeStatusMessageLifecycle() {
        statusMessagePresenter.resume();
    }

    /**
     * Звільняє власні слухачі, від'єднує ресурси робочої області та прибирає таблицю стилів із керованої сцени.
     * Повторне виконання безпечне; ресурси накладки меню звільняє її власний контролер.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        root.getChildren().removeListener(rootLayerOrderListener);
        toolbar.dispose();
        workspace.dispose();
        statusMessagePresenter.dispose();
        removeOwnedStylesheet();
    }

    /**
     * Прибирає зі сцени лише таблицю стилів, яку додало це подання, не втручаючись у зовнішні таблиці.
     */
    private void removeOwnedStylesheet() {
        if (createdScene != null && ownedStylesheetUrl != null) {
            createdScene.getStylesheets().remove(ownedStylesheetUrl);
        }
        createdScene = null;
        ownedStylesheetUrl = null;
    }

    /** Повертає кореневий вузол інтерфейсу. */
    public StackPane node() {
        return root;
    }

    /** Повертає робочу область редактора. */
    public WorkspaceView workspace() {
        return workspace;
    }

    /** Повертає панель інструментів. */
    public ToolbarView toolbar() {
        return toolbar;
    }

    /** Повертає накладку головного меню. */
    public MainMenuView mainMenu() {
        return mainMenu;
    }

    /** Передає текст до рядка стану та запускає життєвий цикл повідомлення. */
    public void setStatus(String text) {
        statusMessagePresenter.show(text);
    }

    /** Оновлює розмиття, доступність і графічний шар, коли відкрита накладка головного меню. */
    private void setMainMenuVisualState(boolean visible) {
        content.setDisable(visible);
        content.setEffect(visible ? new GaussianBlur(7.0) : null);
        if (visible) {
            keepMenuAboveOverlays();
        }
    }

    /** Повертає головне меню на передній план без рекурсивної перебудови списку вузлів. */
    private void keepMenuAboveOverlays() {
        if (normalizingRootLayerOrder || !mainMenu.isMenuVisible() || root.getChildren().isEmpty()
                || root.getChildren().get(root.getChildren().size() - 1) == mainMenu) {
            return;
        }
        normalizingRootLayerOrder = true;
        try {
            mainMenu.toFront();
        } finally {
            normalizingRootLayerOrder = false;
        }
    }

    /** Розміщує панель інструментів, область перегляду й рядок стану без зміни порядку графічних шарів. */
    private static final class RootPane extends Pane {

        /** Розміщує панель інструментів, робочу область і рядок стану в межах доступного простору. */
        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();
            if (width <= 0.0 || height <= 0.0) {
                return;
            }
            double toolbarHeight = Math.min(ToolbarView.HEIGHT, height);
            double statusHeight = Math.min(StatusBarView.HEIGHT, Math.max(0.0, height - toolbarHeight));
            double statusY = height - statusHeight;
            double viewportHeight = Math.max(0.0, statusY - toolbarHeight);
            Pane toolbar = (Pane) getChildren().get(0);
            Pane viewport = (Pane) getChildren().get(1);
            Pane status = (Pane) getChildren().get(2);
            toolbar.resizeRelocate(0.0, 0.0, width, toolbarHeight);
            viewport.resizeRelocate(0.0, toolbarHeight, width, viewportHeight);
            status.resizeRelocate(0.0, statusY, width, statusHeight);
        }
    }
}
