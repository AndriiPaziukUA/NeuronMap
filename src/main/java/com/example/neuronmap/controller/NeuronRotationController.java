package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.util.NeuronRotationMath;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronOverlayPositioner;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Керує жестами обертання нейронів і звільняє обробники ручок разом із редактором. */
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
    private final Map<RotationHandleView, HandleHandlers> handleHandlers = new IdentityHashMap<>();

    private String activeNeuronId;
    private String rotatingNeuronId;
    private Point2D rotationCenter;
    private double rotationStartPointerAngle;
    private double rotationStartDegrees;
    private boolean disposed;

    /** Створює контролер обертання та зберігає залежності для керування жестами й оновлення редактора. */
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
        overlayPositioner = new NeuronOverlayPositioner(workspace);
        this.hideMenu = Objects.requireNonNull(hideMenu, "hideMenu");
        this.refreshVisuals = Objects.requireNonNull(refreshVisuals, "refreshVisuals");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.refreshOverlays = Objects.requireNonNull(refreshOverlays, "refreshOverlays");
        this.save = Objects.requireNonNull(save, "save");
    }

    /**
     * Під'єднує стабільні обробники жесту обертання до ручки конкретного нейрона.
     * Повторний виклик для тієї самої ручки не створює додаткових обробників.
     *
     * @param neuronView нейрон, для якого виконується обертання.
     * @param handle ручка обертання, до якої приєднуються обробники.
     * @throws NullPointerException якщо нейрон або ручку не задано.
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     */
    public void installHandleHandlers(NeuronView neuronView, RotationHandleView handle) {
        ensureNotDisposed();
        Objects.requireNonNull(neuronView, "neuronView");
        Objects.requireNonNull(handle, "handle");
        if (handleHandlers.containsKey(handle)) {
            return;
        }
        String neuronId = neuronView.model().id();
        EventHandler<MouseEvent> pressed = event -> beginRotation(neuronId, event);
        EventHandler<MouseEvent> dragged = event -> dragRotation(neuronId, event);
        EventHandler<MouseEvent> released = event -> endRotation(neuronId, event, handle);
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, pressed);
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, dragged);
        handle.addEventHandler(MouseEvent.MOUSE_RELEASED, released);
        handleHandlers.put(handle, new HandleHandlers(pressed, dragged, released));
    }

    /** Знімає всі обробники вказаної ручки та скидає жест, якщо він був активний на ній. */
    public void uninstallHandleHandlers(RotationHandleView handle) {
        if (handle == null) {
            return;
        }
        HandleHandlers handlers = handleHandlers.remove(handle);
        if (handlers == null) {
            return;
        }
        handle.removeEventHandler(MouseEvent.MOUSE_PRESSED, handlers.pressedHandler());
        handle.removeEventHandler(MouseEvent.MOUSE_DRAGGED, handlers.draggedHandler());
        handle.removeEventHandler(MouseEvent.MOUSE_RELEASED, handlers.releasedHandler());
        if (activeNeuronId != null && rotationHandles.get(activeNeuronId) == handle) {
            activeNeuronId = null;
        }
        if (rotatingNeuronId != null && rotationHandles.get(rotatingNeuronId) == handle) {
            resetRotationGesture();
            state.clearTransientModes();
            workspace.node().setCursor(Cursor.DEFAULT);
        }
    }

    /** Показує ручку обертання для нейрона з указаним ідентифікатором. */
    public void show(String neuronId) {
        ensureNotDisposed();
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

    /** Приховує всі ручки обертання та очищає тимчасовий стан жесту. */
    public void hideAll() {
        for (RotationHandleView handle : rotationHandles.values()) {
            if (handle != null) {
                handle.setVisible(false);
            }
        }
        activeNeuronId = null;
        resetRotationGesture();
    }

    /** Остаточно знімає обробники ручок і відновлює стан курсора. */
    public void dispose() {
        if (disposed) {
            return;
        }
        hideAll();
        for (RotationHandleView handle : handleHandlers.keySet().toArray(RotationHandleView[]::new)) {
            uninstallHandleHandlers(handle);
        }
        state.clearTransientModes();
        workspace.node().setCursor(Cursor.DEFAULT);
        disposed = true;
    }

    /** Перераховує положення активної ручки за поточними координатами й кутом нейрона. */
    public void refreshPosition() {
        if (disposed || activeNeuronId == null) {
            return;
        }
        NeuronView view = neuronViews.get(activeNeuronId);
        RotationHandleView handle = rotationHandles.get(activeNeuronId);
        if (view == null || handle == null || !handle.isVisible()) {
            return;
        }
        overlayPositioner.position(view, null, handle);
    }

    /** Запам'ятовує початковий кут нейрона й покажчика перед початком перетягування ручки. */
    private void beginRotation(String neuronId, MouseEvent event) {
        if (disposed || event.getButton() != MouseButton.PRIMARY) {
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
        state.enterRotateMode();
        workspace.node().setCursor(Cursor.CROSSHAIR);
        event.consume();
    }

    /** Обчислює новий кут і передає його службі предметної логіки. */
    private void dragRotation(String neuronId, MouseEvent event) {
        if (disposed || !neuronId.equals(rotatingNeuronId) || rotationCenter == null) {
            return;
        }
        Point2D mouse = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        double currentPointerAngle = NeuronRotationMath.pointerAngleDegrees(rotationCenter, mouse);
        double degrees = NeuronRotationMath.rotationForDrag(
                rotationStartDegrees, rotationStartPointerAngle, currentPointerAngle);
        if (neuronService.setRotation(neuronId, degrees)) {
            refreshVisuals.run();
            refreshConnections.run();
            refreshOverlays.run();
        }
        event.consume();
    }

    /** Завершує обертання, відновлює курсор і зберігає змінений кут нейрона. */
    private void endRotation(String neuronId, MouseEvent event, RotationHandleView handle) {
        if (disposed || !neuronId.equals(rotatingNeuronId)) {
            return;
        }
        resetRotationGesture();
        state.clearTransientModes();
        workspace.node().setCursor(Cursor.DEFAULT);
        activeNeuronId = neuronId;
        handle.setVisible(true);
        refreshPosition();
        save.run();
        event.consume();
    }

    /** Скидає координати й ідентифікатор поточного жесту без запуску збереження. */
    private void resetRotationGesture() {
        rotatingNeuronId = null;
        rotationCenter = null;
        rotationStartPointerAngle = 0.0;
        rotationStartDegrees = 0.0;
    }

    /** Забороняє реєстрацію нових обробників після завершення життєвого циклу. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron rotation controller is disposed");
        }
    }

    /** Групує три обробники ручки, щоб точно від'єднати їх під час вилучення подання. */
    private record HandleHandlers(
            EventHandler<MouseEvent> pressedHandler,
            EventHandler<MouseEvent> draggedHandler,
            EventHandler<MouseEvent> releasedHandler
    ) { }
}
