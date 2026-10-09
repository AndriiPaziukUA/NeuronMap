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
 * Збирає основні елементи вікна редактора: полотно, панель інструментів, головне меню та рядок стану.
 */
public final class MainView {

    private final StackPane root = new StackPane();
    private final RootPane content = new RootPane();
    private final ToolbarView toolbar;
    private final WorkspaceView workspace = new WorkspaceView();
    private final StatusBarView statusBar = new StatusBarView();
    private final MainMenuView mainMenu;

    /**
     * Створює екземпляр MainView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param addNeuron значення «add neuron», яке використовується в цьому методі.
     * @param group значення «group», яке використовується в цьому методі.
     * @param ungroup значення «ungroup», яке використовується в цьому методі.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
     * @param pauseResume значення «pause resume», яке використовується в цьому методі.
     * @param stopSignals значення «stop signals», яке використовується в цьому методі.
     * @param speedChanged значення «speed changed», яке використовується в цьому методі.
     * @param initialSpeedMillis значення «initial speed millis», яке використовується в цьому методі.
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
     * Створює екземпляр MainView та зберігає передані залежності, потрібні для його роботи.
     *
     * @param addNeuron значення «add neuron», яке використовується в цьому методі.
     * @param group значення «group», яке використовується в цьому методі.
     * @param ungroup значення «ungroup», яке використовується в цьому методі.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
     * @param pauseResume значення «pause resume», яке використовується в цьому методі.
     * @param stopSignals значення «stop signals», яке використовується в цьому методі.
     * @param speedChanged значення «speed changed», яке використовується в цьому методі.
     * @param initialSpeedMillis значення «initial speed millis», яке використовується в цьому методі.
     * @param localization служба локалізації інтерфейсу.
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
     * Створює сцену з кореневим вузлом інтерфейсу й заданими початковими розмірами.
     *
     * @param width ширина видимої області.
     * @param height висота видимої області.
     */
    public Scene createScene(double width, double height) {
        Scene scene = new Scene(root, width, height);

        URL css = getClass().getResource("/app.css");
        if (css == null) {

            throw new IllegalStateException("app.css is missing");
        }

        scene.getStylesheets().add(css.toExternalForm());
        root.layout();
        return scene;
    }

    /**
     * Повертає кореневий вузол інтерфейсу.
     *
     * @return кореневий вузол інтерфейсу.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Повертає полотно редактора.
     *
     * @return полотно редактора.
     */
    public WorkspaceView workspace() {
        return workspace;
    }

    /**
     * Повертає панель інструментів.
     *
     * @return панель інструментів.
     */
    public ToolbarView toolbar() {
        return toolbar;
    }

    /**
     * Повертає головне меню.
     *
     * @return головне меню.
     */
    public MainMenuView mainMenu() {
        return mainMenu;
    }

    /**
     * Установлює status для поточного об’єкта.
     *
     * @param text текст, який потрібно показати або розібрати.
     */
    public void setStatus(String text) {
        statusBar.setText(text);
    }

    /**
     * Установлює main menu visual state для поточного об’єкта.
     *
     * @param visible значення «visible», яке використовується в цьому методі.
     */
    private void setMainMenuVisualState(boolean visible) {
        content.setDisable(visible);
        content.setEffect(
                visible ? new GaussianBlur(7.0) : null
        );
    }

    /**
     * Розміщує дочірні елементи головного вікна редактора під час зміни розміру сцени.
     */
    private static final class RootPane extends Pane {

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
