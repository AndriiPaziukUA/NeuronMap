package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronOverlayPositioner;
import com.example.neuronmap.view.NeuronSettingsDialog;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/** Builds and manages the neuron context menu; contains no domain rules. */
public final class NeuronMenuController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final ConnectionController connections;
    private final SimulationController simulation;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuCustomizer menuCustomizer;
    private final NeuronOverlayPositioner overlayPositioner;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlays;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    private VBox menu;
    private String activeNeuronId;

    public NeuronMenuController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            ConnectionController connections,
            SimulationController simulation,
            NeuronDeletionController deletionController,
            NeuronMenuCustomizer menuCustomizer,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                neuronService,
                state,
                workspace,
                connections,
                simulation,
                deletionController,
                menuCustomizer,
                refreshVisuals,
                refreshConnections,
                refreshOverlays,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    public NeuronMenuController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            ConnectionController connections,
            SimulationController simulation,
            NeuronDeletionController deletionController,
            NeuronMenuCustomizer menuCustomizer,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
        this.menuCustomizer = Objects.requireNonNull(menuCustomizer, "menuCustomizer");
        this.overlayPositioner = new NeuronOverlayPositioner(workspace);
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlays = Objects.requireNonNull(refreshOverlays, "refreshOverlays");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    public boolean isVisible() {
        return menu != null && menu.isVisible();
    }

    public VBox node() {
        return menu;
    }

    public void show(NeuronView neuronView) {
        hide();

        String neuronId = neuronView.model().id();
        state.setSelectedNeuronForMenu(neuronId);
        activeNeuronId = neuronId;

        menu = new VBox(5);
        menu.getStyleClass().add("connection-menu");

        HBox actions = new HBox(4);
        actions.setAlignment(Pos.CENTER);

        Button fire = menuButton("⚡", localization.text("neuron.menu.fire"), "neuron-menu-activate");
        Button addConnection = menuButton("↗", localization.text("neuron.menu.add_connection"), "");
        Button settings = menuButton("⚙", localization.text("neuron.menu.settings"), "");
        Button deleteConnection = null;
        if (connections.hasConnections(neuronId)) {
            deleteConnection = menuButton("⛓", localization.text("neuron.menu.delete_connection"), "");
        }
        Button direction = menuCustomizer.createDirectionButton();
        Button toggleType = menuButton("⇄", localization.text("neuron.menu.toggle_type"), "");
        Button deleteNeuron = menuButton("✕", localization.text("neuron.menu.delete"), "neuron-menu-delete");

        fire.setOnAction(event -> {
            simulation.emitPulse(neuronId);
            event.consume();
        });

        addConnection.setOnAction(event -> {
            hide();
            connections.beginCreate(neuronId);
            event.consume();
        });

        settings.setOnAction(event -> {
            var neuron = neuronService.find(neuronId);
            if (neuron != null) {
                NeuronSettingsDialog.show(
                        workspace.node().getScene() == null
                                ? null
                                : workspace.node().getScene().getWindow(),
                        neuron,
                        localization
                ).ifPresent(values -> {
                    neuronService.updateSettings(
                            neuronId,
                            values.signalStrength(),
                            values.activationThreshold()
                    );
                    save.run();
                    status.accept(localization.text("neuron.settings.saved"));
                });
            }
            event.consume();
        });

        if (deleteConnection != null) {
            deleteConnection.setOnAction(event -> {
                hide();
                connections.beginDelete(neuronId);
                event.consume();
            });
        }

        toggleType.setOnAction(event -> {
            neuronService.toggleType(neuronId);
            refreshVisuals.run();
            refreshConnections.run();
            refreshOverlays.run();
            save.run();
            event.consume();
        });

        deleteNeuron.setOnAction(event -> {
            deletionController.deleteNeuron(neuronId);
            event.consume();
        });

        actions.getChildren().addAll(fire, addConnection, settings);
        if (deleteConnection != null) {
            actions.getChildren().add(deleteConnection);
        }
        if (direction != null) {
            actions.getChildren().add(direction);
        }
        actions.getChildren().addAll(toggleType, deleteNeuron);

        menu.getChildren().add(actions);
        workspace.overlayLayer().getChildren().add(menu);
        overlayPositioner.position(neuronView, menu, null);
    }

    public void hide() {
        if (menu != null) {
            workspace.overlayLayer().getChildren().remove(menu);
            menu = null;
        }
        activeNeuronId = null;
        state.setSelectedNeuronForMenu(null);
    }

    public void refreshPosition(NeuronView view) {
        if (!isVisible() || activeNeuronId == null || view == null) {
            return;
        }
        if (!activeNeuronId.equals(view.model().id())) {
            return;
        }
        overlayPositioner.position(view, menu, null);
    }

    private Button menuButton(String text, String tooltipText, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add("menu-button");
        if (styleClass != null && !styleClass.isBlank()) {
            button.getStyleClass().add(styleClass);
        }
        button.setTooltip(new Tooltip(tooltipText));
        menuCustomizer.customizeMenuButton(button);
        return button;
    }
}
