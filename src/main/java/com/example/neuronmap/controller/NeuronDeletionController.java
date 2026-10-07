package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Deletes the current neuron selection without depending on the context menu. */
public final class NeuronDeletionController {

    private final NeuronMapApplicationService application;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final Runnable hideMenu;
    private final Runnable refreshVisuals;
    private final Runnable refreshPresentation;
    private final Runnable refreshDeleteHighlights;
    private final Runnable save;
    private final Consumer<String> status;
    private final EventHandler<KeyEvent> keyHandler =
            this::handleKeyPressed;

    public NeuronDeletionController(
            NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            Runnable hideMenu,
            Runnable refreshVisuals,
            Runnable refreshPresentation,
            Runnable refreshDeleteHighlights,
            Runnable save,
            Consumer<String> status
    ) {
        this.application = application;
        this.state = state;
        this.workspace = workspace;
        this.neuronViews = neuronViews;
        this.rotationHandles = rotationHandles;
        this.hideMenu = hideMenu;
        this.refreshVisuals = refreshVisuals;
        this.refreshPresentation = refreshPresentation;
        this.refreshDeleteHighlights = refreshDeleteHighlights;
        this.save = save;
        this.status = status;
        installSceneKeyHandler();
    }

    private void installSceneKeyHandler() {
        workspace.node().sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
                    if (oldScene != null) {
                        oldScene.removeEventFilter(
                                KeyEvent.KEY_PRESSED,
                                keyHandler
                        );
                    }

                    if (newScene != null) {
                        newScene.addEventFilter(
                                KeyEvent.KEY_PRESSED,
                                keyHandler
                        );
                    }
                }
        );
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.DELETE
                || isTextInputTarget(event.getTarget())) {
            return;
        }

        if (deleteSelectedNeurons()) {
            event.consume();
        }
    }

    private boolean isTextInputTarget(Object target) {
        javafx.scene.Node node = target instanceof javafx.scene.Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (node instanceof TextInputControl) {
                return true;
            }
            node = node.getParent();
        }

        return false;
    }

    public boolean deleteSelectedNeurons() {
        LinkedHashSet<String> selectedIds =
                new LinkedHashSet<>(state.selectedNeuronIds());

        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }

        selectedIds.removeIf(
                id -> application.model().neuron(id) == null
        );

        if (selectedIds.isEmpty()) {
            return false;
        }

        deleteNeurons(selectedIds);
        return true;
    }

    public void deleteNeuron(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return;
        }

        deleteNeurons(Set.of(neuronId));
    }

    private void deleteNeurons(Collection<String> neuronIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(neuronIds);
        boolean changed = false;

        hideMenu.run();

        for (String neuronId : ids) {
            if (application.removeNeuron(neuronId) == null) {
                continue;
            }

            changed = true;
            neuronViews.remove(neuronId);

            RotationHandleView removedHandle = rotationHandles.remove(neuronId);
            if (removedHandle != null) {
                removedHandle.dispose();
                workspace.overlayLayer().getChildren().remove(removedHandle);
            }
        }

        if (!changed) {
            return;
        }

        workspace.nodeLayer().getChildren().removeIf(
                node -> node instanceof NeuronView neuronView
                        && application.model().neuron(
                                neuronView.model().id()
                        ) == null
        );

        state.clearSelection();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);

        refreshVisuals.run();
        refreshPresentation.run();
        refreshDeleteHighlights.run();
        save.run();

        status.accept(
                ids.size() == 1
                        ? "Нейрон та його зв'язки видалено."
                        : "Вибрані нейрони та їх зв'язки видалено."
        );
    }
}
