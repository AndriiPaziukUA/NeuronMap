package com.example.neuronmap.controller;

import com.example.neuronmap.view.TooltipFactory;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Applies consistent styling and creates neuron-menu actions. */
public final class NeuronMenuCustomizer {

    private final Node toolbarRoot;
    private final Supplier<String> selectedNeuronIdSupplier;
    private final Consumer<String> directionAction;

    public NeuronMenuCustomizer(Node toolbarRoot) {
        this(toolbarRoot, () -> null, null);
    }

    public NeuronMenuCustomizer(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction
    ) {
        if (toolbarRoot == null) {
            throw new IllegalArgumentException(
                    "toolbarRoot must not be null"
            );
        }

        this.toolbarRoot = toolbarRoot;
        this.selectedNeuronIdSupplier =
                selectedNeuronIdSupplier == null
                        ? () -> null
                        : selectedNeuronIdSupplier;
        this.directionAction = directionAction;
    }

    public void customizeMenuButton(Button button) {
        if (button == null) {
            return;
        }

        if (button.getStyleClass().contains("neuron-menu-activate")) {
            button.setStyle(
                    "-fx-background-color: #f5c542; "
                            + "-fx-text-fill: black;"
            );
            TooltipFactory.install(
                    button,
                    "Відправити імпульс"
            );
            return;
        }

        if (button.getStyleClass().contains("neuron-menu-delete")) {
            button.setStyle(
                    "-fx-background-color: #d64545; "
                            + "-fx-text-fill: white;"
            );
        }
    }

    /**
     * Creates the direction button before the menu is added to the overlay.
     * This guarantees that the initial menu width is already final.
     */
    public Button createDirectionButton() {
        if (directionAction == null) {
            return null;
        }

        Button directionButton = new Button("↔");
        directionButton.getStyleClass().add("menu-button");
        directionButton.getStyleClass().add("neuron-menu-direction");
        directionButton.setMinSize(32.0, 32.0);
        directionButton.setPrefSize(32.0, 32.0);
        directionButton.setMaxSize(32.0, 32.0);
        directionButton.setStyle(
                "-fx-background-color: #4c5360; "
                        + "-fx-text-fill: white;"
        );
        directionButton.setOnAction(event -> {
            String neuronId = selectedNeuronIdSupplier.get();
            if (neuronId != null) {
                directionAction.accept(neuronId);
            }
            event.consume();
        });
        TooltipFactory.install(
                directionButton,
                "Змінити напрямок"
        );
        return directionButton;
    }

    public void removeDeleteConnectionModeExitButton() {
        toolbarRoot.lookupAll(
                ".delete-connection-exit-button"
        ).forEach(node -> {
            if (node.getParent() instanceof Pane pane) {
                pane.getChildren().remove(node);
            }
        });
    }
}
