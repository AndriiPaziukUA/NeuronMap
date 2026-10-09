package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronOverlayPositioner;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;

/**
 * Обробляє обертання нейрона через графічний інтерфейс.
 */
public final class NeuronRotationController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final NeuronOverlayPositioner overlayPositioner;
    private final Runnable hideMenu;
    private final Runnable refreshVisuals;
    private final Runnable refreshConnections;
    private final Runnable refreshOverlays;
    private final Runnable save;

    private String activeNeuronId;
    private String rotatingNeuronId;
    private Point2D rotationCenter;
    private double rotationStartPointerAngle;
    private double rotationStartDegrees;

    /**
     * Повертає результат операції «нейрон обертання».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param hideMenu значення, що визначає приховати меню для цієї операції.
     *
     * @param refreshVisuals значення, що визначає відповідну операцію для цієї операції.
     *
     * @param refreshConnections значення, що визначає звʼязки для цієї операції.
     *
     * @param refreshOverlays значення, що визначає накладки для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronRotationController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            Runnable hideMenu,
            Runnable refreshVisuals,
            Runnable refreshConnections,
            Runnable refreshOverlays,
            Runnable save
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.rotationHandles = Objects.requireNonNull(rotationHandles, "rotationHandles");
        this.overlayPositioner = new NeuronOverlayPositioner(workspace);
        this.hideMenu = Objects.requireNonNull(hideMenu, "hideMenu");
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlays = Objects.requireNonNull(refreshOverlays, "refreshOverlays");
        this.save = Objects.requireNonNull(save, "save");
    }

    /**
     * Створює обʼєкт із переданих даних «обробити».
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     */
    public void createHandle(NeuronView neuronView) {
        String neuronId = neuronView.model().id();
        RotationHandleView handle = new RotationHandleView();
        handle.setVisible(false);
        rotationHandles.put(neuronId, handle);
        workspace.overlayLayer().getChildren().add(handle);

        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> beginRotation(neuronId, event, handle));
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> dragRotation(neuronId, event));
        handle.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> endRotation(neuronId, event, handle));
    }

    /**
     * Відображає «потрібні дані» в інтерфейсі.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void show(String neuronId) {
        hideMenu.run();
        hideAll();
        activeNeuronId = neuronId;
        RotationHandleView handle = rotationHandles.get(neuronId);
        if (handle == null) {
            return;
        }
        handle.setVisible(true);
        handle.toFront();
        refreshPosition();
    }

    /**
     * Виконує операцію «приховати усі».
     */
    public void hideAll() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setVisible(false);
            }
        }
        activeNeuronId = null;
        rotatingNeuronId = null;
        rotationCenter = null;
    }

    /**
     * Обробляє «положення».
     */
    public void refreshPosition() {
        if (activeNeuronId == null) {
            return;
        }
        NeuronView view = neuronViews.get(activeNeuronId);
        RotationHandleView handle = rotationHandles.get(activeNeuronId);
        if (view == null || handle == null || !handle.isVisible()) {
            return;
        }
        overlayPositioner.position(view, null, handle);
    }

    /**
     * Запускає або планує дію, повʼязану з «обертання».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param event подія інтерфейсу.
     *
     * @param handle значення, що визначає обробити для цієї операції.
     */
    private void beginRotation(String neuronId, MouseEvent event, RotationHandleView handle) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        NeuronView view = neuronViews.get(neuronId);
        if (view == null) {
            return;
        }

        rotationCenter = overlayPositioner.rectangleCenterIn(view, workspace.node());
        Point2D pointer = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        rotationStartPointerAngle = NeuronRotationMath.pointerAngleDegrees(rotationCenter, pointer);
        rotationStartDegrees = view.presentation().rotationDegrees();
        rotatingNeuronId = neuronId;
        activeNeuronId = neuronId;
        state.enterRotateMode(neuronId);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        event.consume();
    }

    /**
     * Переміщує обʼєкт «обертання» відповідно до переданого зміщення.
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param event подія інтерфейсу.
     */
    private void dragRotation(String neuronId, MouseEvent event) {
        if (!neuronId.equals(rotatingNeuronId) || rotationCenter == null) {
            return;
        }

        Point2D mouse = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        double currentPointerAngle = NeuronRotationMath.pointerAngleDegrees(rotationCenter, mouse);
        double degrees = NeuronRotationMath.rotationForDrag(
                rotationStartDegrees,
                rotationStartPointerAngle,
                currentPointerAngle
        );

        if (neuronService.setRotation(neuronId, degrees)) {
            refreshVisuals.run();
            refreshConnections.run();
            refreshOverlays.run();
        }
        event.consume();
    }

    /**
     * Виконує операцію «обертання».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param event подія інтерфейсу.
     *
     * @param handle значення, що визначає обробити для цієї операції.
     */
    private void endRotation(String neuronId, MouseEvent event, RotationHandleView handle) {
        if (!neuronId.equals(rotatingNeuronId)) {
            return;
        }

        rotatingNeuronId = null;
        rotationCenter = null;
        rotationStartPointerAngle = 0.0;
        rotationStartDegrees = 0.0;
        state.clearTransientModes();
        workspace.node().setCursor(Cursor.DEFAULT);
        activeNeuronId = neuronId;
        handle.setVisible(true);
        refreshPosition();
        save.run();
        event.consume();
    }
}
