package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;
import java.util.function.Consumer;

/** Handles dragging an existing neuron or an entire neuron group. */
public final class NeuronDragController {

    private final NeuronMapApplicationService application;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Consumer<String> showRotationHandle;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlayPositions;
    private final Runnable save;

    public NeuronDragController(
            NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<String> showRotationHandle,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlayPositions,
            Runnable save
    ) {
        this.application = Objects.requireNonNull(application, "application");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.hideMenu = Objects.requireNonNull(hideMenu, "hideMenu");
        this.showRotationHandle = Objects.requireNonNull(showRotationHandle, "showRotationHandle");
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlayPositions = Objects.requireNonNull(refreshOverlayPositions, "refreshOverlayPositions");
        this.save = Objects.requireNonNull(save, "save");
    }

    public void handlePressed(NeuronView neuronView, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (!state.isIdle()) {
                return;
            }

            hideMenu.run();
            event.consume();
            return;
        }

        if (event.getButton() != MouseButton.PRIMARY || !state.isIdle()) {
            return;
        }

        hideMenu.run();

        String neuronId = neuronView.model().id();
        if (event.isControlDown()) {
            state.toggleSelection(neuronId);
        } else if (!state.selectedNeuronIds().contains(neuronId)) {
            state.selectOnly(neuronId);
        }
        refreshVisuals.run();

        showRotationHandle.accept(neuronId);

        NeuronGroup group = application.model().groupContaining(neuronId);
        neuronView.beginDrag(
                event.getSceneX(),
                event.getSceneY(),
                event.isAltDown(),
                group != null
        );
        event.consume();
    }

    public void handleDragged(NeuronView neuronView, MouseEvent event) {
        if (!neuronView.isDragging()) {
            return;
        }

        neuronView.updateDraggedState(
                event.getSceneX(),
                event.getSceneY()
        );

        double dx = neuronView.dragDeltaX(event.getSceneX()) / state.zoom();
        double dy = neuronView.dragDeltaY(event.getSceneY()) / state.zoom();
        String neuronId = neuronView.model().id();

        if (neuronView.isDraggingGroup()) {
            NeuronGroup group = application.model().groupContaining(neuronId);
            if (group != null) {
                for (String memberId : group.memberIds()) {
                    NeuronPresentation presentation =
                            application.model().presentation(memberId);
                    if (presentation != null) {
                        presentation.moveBy(dx, dy);
                    }
                }
            }
        } else {
            neuronView.presentation().moveBy(dx, dy);
        }

        refreshVisuals.run();
        refreshConnections.run();
        refreshOverlayPositions.run();
        event.consume();
    }

    public void handleReleased(NeuronView neuronView, MouseEvent event) {
        boolean dragged = neuronView.wasDragged();
        neuronView.endDrag();

        if (dragged) {
            workspace.node().setCursor(Cursor.DEFAULT);
            refreshOverlayPositions.run();
            save.run();
        }
    }
}
