package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;
import java.util.function.Consumer;

/** Обробляє перетягування нейронів і груп, синхронізуючи залежну геометрію та завершення жесту. */
public final class NeuronDragController {

    private final NeuronService neuronService;
    private final GroupService groupService;
    private final SelectionController selectionController;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Consumer<String> showRotationHandle;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlayPositions;
    private final Runnable save;

    /**
     * Створює контролер перетягування зі службами моделі та діями синхронізації подань.
     *
     * @param neuronService служба операцій над нейронами.
     * @param groupService служба операцій над групами нейронів.
     * @param selectionController контролер вибору нейронів.
     * @param state стан взаємодії редактора.
     * @param workspace полотно редактора.
     * @param hideMenu дія приховування контекстного меню.
     * @param showRotationHandle дія показу ручки обертання.
     * @param refreshVisuals дія оновлення подань нейронів.
     * @param refreshConnections дія оновлення геометрії зв'язків.
     * @param refreshOverlayPositions дія оновлення позицій накладок.
     * @param save дія збереження карти після завершення перетягування.
     */
    public NeuronDragController(
            NeuronService neuronService,
            GroupService groupService,
            SelectionController selectionController,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<String> showRotationHandle,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlayPositions,
            Runnable save
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.hideMenu = Objects.requireNonNull(hideMenu, "hideMenu");
        this.showRotationHandle = Objects.requireNonNull(showRotationHandle, "showRotationHandle");
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlayPositions = Objects.requireNonNull(refreshOverlayPositions, "refreshOverlayPositions");
        this.save = Objects.requireNonNull(save, "save");
    }

    /** Починає обробку натискання миші для вибору та перетягування нейрона. */
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
        selectionController.selectForPrimaryPress(neuronView.model().id(), event.isControlDown());
        boolean grouped = groupService.containing(neuronView.model().id()).isPresent();
        neuronView.beginDrag(event.getSceneX(), event.getSceneY(), event.isAltDown(), grouped);
        showRotationHandle.accept(neuronView.model().id());
        event.consume();
    }

    /** Переміщує нейрон або його групу та синхронізує залежну геометрію. */
    public void handleDragged(NeuronView neuronView, MouseEvent event) {
        if (!neuronView.isDragging()) {
            return;
        }
        neuronView.updateDraggedState(event.getSceneX(), event.getSceneY());
        double dx = neuronView.dragDeltaX(event.getSceneX()) / state.zoom();
        double dy = neuronView.dragDeltaY(event.getSceneY()) / state.zoom();
        String neuronId = neuronView.model().id();
        if (neuronView.isDraggingGroup()) {
            groupService.moveContaining(neuronId, dx, dy);
        } else {
            neuronService.move(neuronId, dx, dy);
        }
        refreshVisuals.run();
        refreshConnections.run();
        refreshOverlayPositions.run();
        event.consume();
    }

    /** Завершує перетягування та зберігає змінену карту, якщо положення нейрона змінилося. */
    public void handleReleased(NeuronView neuronView) {
        boolean dragged = neuronView.wasDragged();
        neuronView.endDrag();
        if (dragged) {
            workspace.node().setCursor(Cursor.DEFAULT);
            refreshOverlayPositions.run();
            save.run();
        }
    }

    /**
     * Скасовує незавершений жест без збереження та відновлює курсор під час вилучення подання.
     *
     * @param neuronView подання, перетягування якого потрібно скасувати.
     */
    void cancelDragging(NeuronView neuronView) {
        if (neuronView == null || !neuronView.isDragging()) {
            return;
        }
        neuronView.endDrag();
        workspace.node().setCursor(Cursor.DEFAULT);
    }
}
