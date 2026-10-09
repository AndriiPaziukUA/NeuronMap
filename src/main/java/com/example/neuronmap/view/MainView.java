package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Assembles the editor workspace, toolbar, status bar, and main-menu overlay into the application scene.
 */
public final class MainView {

    private final StackPane root = new StackPane();
    private final RootPane content = new RootPane();
    private final ToolbarView toolbar;
    private final WorkspaceView workspace = new WorkspaceView();
    private final StatusBarView statusBar = new StatusBarView();
    private final MainMenuView mainMenu;

    /**
     * Creates the editor view using English as the default UI language.
     *
     * @param addNeuron action for adding a neuron of the selected type.
     * @param group action for grouping the current selection.
     * @param ungroup action for ungrouping the current selection.
     * @param exitDelete action for leaving connection-deletion mode.
     * @param pauseResume action for toggling simulation pause state.
     * @param stopSignals action for stopping signal transmission.
     * @param speedChanged callback for a changed simulation speed value.
     * @param initialSpeedMillis initial simulation tick duration in milliseconds.
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
        this(addNeuron, group, ungroup, exitDelete, pauseResume, stopSignals,
                speedChanged, initialSpeedMillis, new LocalizationService(Locale.ENGLISH));
    }

    /**
     * Creates the editor view with the supplied localization service.
     *
     * @param addNeuron action for adding a neuron of the selected type.
     * @param group action for grouping the current selection.
     * @param ungroup action for ungrouping the current selection.
     * @param exitDelete action for leaving connection-deletion mode.
     * @param pauseResume action for toggling simulation pause state.
     * @param stopSignals action for stopping signal transmission.
     * @param speedChanged callback for a changed simulation speed value.
     * @param initialSpeedMillis initial simulation tick duration in milliseconds.
     * @param localization service providing localized UI messages.
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
                addNeuron, group, ungroup, exitDelete, pauseResume, stopSignals,
                speedChanged, initialSpeedMillis, localization);
        mainMenu = new MainMenuView(localization, this::setMainMenuVisualState);

        content.getStyleClass().add("root");
        content.getChildren().addAll(toolbar.node(), workspace.node(), statusBar.node());
        content.setPickOnBounds(true);
        content.requestLayout();

        root.getStyleClass().add("root");
        root.getChildren().addAll(content, mainMenu);
        root.setPickOnBounds(true);
    }

    /**
     * Creates a JavaFX scene and attaches the application's main stylesheet.
     *
     * @param width initial scene width.
     * @param height initial scene height.
     * @return the configured editor scene.
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
     * Returns the root node of the editor UI.
     *
     * @return editor root node.
     */
    public StackPane node() {
        return root;
    }

    /**
     * Returns the editor workspace.
     *
     * @return workspace view.
     */
    public WorkspaceView workspace() {
        return workspace;
    }

    /**
     * Returns the toolbar.
     *
     * @return editor toolbar view.
     */
    public ToolbarView toolbar() {
        return toolbar;
    }

    /**
     * Returns the main-menu overlay.
     *
     * @return menu view.
     */
    public MainMenuView mainMenu() {
        return mainMenu;
    }

    /** Updates the text displayed in the status bar. */
    public void setStatus(String text) {
        statusBar.setText(text);
    }

    /** Blurs and disables editor content whenever the main-menu overlay is visible. */
    private void setMainMenuVisualState(boolean visible) {
        content.setDisable(visible);
        content.setEffect(visible ? new GaussianBlur(7.0) : null);
    }

    /** Lays out the toolbar, workspace viewport, and status bar within the available scene bounds. */
    private static final class RootPane extends Pane {

        /** Positions the toolbar, main viewport, and status bar without changing their layer order. */
        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();
            if (width <= 0.0 || height <= 0.0) {
                return;
            }

            double toolbarHeight = Math.min(ToolbarView.HEIGHT, height);
            double statusHeight = Math.min(StatusBarView.HEIGHT,
                    Math.max(0.0, height - toolbarHeight));
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
