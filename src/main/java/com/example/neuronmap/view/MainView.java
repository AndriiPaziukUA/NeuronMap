package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.function.Consumer;

/**
 * Створює головне JavaFX-представлення вікна застосунку.
 */
public final class MainView {

    private final StackPane root = new StackPane();
    private final RootPane content = new RootPane();
    private final ToolbarView toolbar;
    private final WorkspaceView workspace = new WorkspaceView();
    private final StatusBarView statusBar = new StatusBarView();
    private final MainMenuView mainMenu;

    /**
     * Повертає результат операції «відображення».
     *
     * @param addNeuron значення, що визначає додати нейрон для цієї операції.
     *
     * @param group група нейронів.
     *
     * @param ungroup значення, що визначає відповідну операцію для цієї операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     *
     * @param pauseResume значення, що визначає відповідну операцію для цієї операції.
     *
     * @param stopSignals значення, що визначає зупинити сигнали для цієї операції.
     *
     * @param speedChanged значення, що визначає швидкість для цієї операції.
     *
     * @param initialSpeedMillis значення, що визначає швидкість для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MainView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable exitDelete,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis
    ) {
        this(
                addNeuron,
                group,
                ungroup,
                exitDelete,
                pauseResume,
                stopSignals,
                speedChanged,
                initialSpeedMillis,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «відображення».
     *
     * @param addNeuron значення, що визначає додати нейрон для цієї операції.
     *
     * @param group група нейронів.
     *
     * @param ungroup значення, що визначає відповідну операцію для цієї операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     *
     * @param pauseResume значення, що визначає відповідну операцію для цієї операції.
     *
     * @param stopSignals значення, що визначає зупинити сигнали для цієї операції.
     *
     * @param speedChanged значення, що визначає швидкість для цієї операції.
     *
     * @param initialSpeedMillis значення, що визначає швидкість для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MainView(
            Consumer<NeuronType> addNeuron,
            Runnable group,
            Runnable ungroup,
            Runnable exitDelete,
            Runnable pauseResume,
            Runnable stopSignals,
            Consumer<String> speedChanged,
            double initialSpeedMillis,
            LocalizationService localization
    ) {
        toolbar = new ToolbarView(
                addNeuron,
                group,
                ungroup,
                exitDelete,
                pauseResume,
                stopSignals,
                speedChanged,
                initialSpeedMillis,
                localization
        );

        mainMenu = new MainMenuView(
                localization,
                this::setMainMenuVisualState
        );

        content.getStyleClass().add("root");
        content.getChildren().addAll(
                toolbar.node(),
                workspace.node(),
                statusBar.node()
        );
        content.setPickOnBounds(true);
        content.requestLayout();

        root.getStyleClass().add("root");
        root.getChildren().addAll(content, mainMenu);
        root.setPickOnBounds(true);
    }

    /**
     * Створює обʼєкт із переданих даних «сцена».
     *
     * @param width ширина області.
     *
     * @param height висота області.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Scene createScene(double width, double height) {
        Scene scene = new Scene(root, width, height);

        URL css = getClass().getResource("/app.css");
        if (css == null) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("app.css is missing");
        }

        scene.getStylesheets().add(css.toExternalForm());
        root.layout();
        return scene;
    }

    /**
     * Повертає результат операції «вузол».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public WorkspaceView workspace() {
        return workspace;
    }

    /**
     * Повертає результат операції «панель інструментів».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ToolbarView toolbar() {
        return toolbar;
    }

    /**
     * Повертає результат операції «меню».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MainMenuView mainMenu() {
        return mainMenu;
    }

    /**
     * Задає або оновлює значення, повʼязані з «стан».
     *
     * @param text текст, який потрібно показати або обробити.
     */
    public void setStatus(String text) {
        statusBar.setText(text);
    }

    /**
     * Задає або оновлює значення, повʼязані з «меню графічний стан».
     *
     * @param visible ознака видимості елемента.
     */
    private void setMainMenuVisualState(boolean visible) {
        content.setDisable(visible);
        content.setEffect(
                visible ? new GaussianBlur(7.0) : null
        );
    }

    /**
     * Компонент RootPane у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    private static final class RootPane extends Pane {
        /**
         * Виконує операцію «розташування».
         */
        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();

            if (width <= 0.0 || height <= 0.0) {
                return;
            }

            double toolbarHeight = Math.min(
                    ToolbarView.HEIGHT,
                    height
            );

            double statusHeight = Math.min(
                    StatusBarView.HEIGHT,
                    Math.max(0.0, height - toolbarHeight)
            );

            double statusY = height - statusHeight;
            double viewportHeight = Math.max(
                    0.0,
                    statusY - toolbarHeight
            );

            Pane toolbar = (Pane) getChildren().get(0);
            Pane viewport = (Pane) getChildren().get(1);
            Pane status = (Pane) getChildren().get(2);

            toolbar.resizeRelocate(
                    0.0,
                    0.0,
                    width,
                    toolbarHeight
            );
            viewport.resizeRelocate(
                    0.0,
                    toolbarHeight,
                    width,
                    viewportHeight
            );
            status.resizeRelocate(
                    0.0,
                    statusY,
                    width,
                    statusHeight
            );
        }
    }
}
