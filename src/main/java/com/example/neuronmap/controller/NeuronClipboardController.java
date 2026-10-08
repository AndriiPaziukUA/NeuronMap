package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.WindowEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Coordinates keyboard/mouse clipboard events and delegates copy/paste use cases. */
public final class NeuronClipboardController {

    private final NeuronClipboardService clipboardService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Consumer<Neuron> addView;
    private final Runnable refreshPresentation;
    private final Runnable save;
    private final Consumer<String> status;

    private final EventHandler<KeyEvent> keyHandler;
    private final EventHandler<WindowEvent> windowHiddenHandler;

    private boolean mouseInsideWorkspace;
    private double lastMouseX;
    private double lastMouseY;

    public NeuronClipboardController(
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<Neuron> addView,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status
    ) {
        this.clipboardService = clipboardService;
        this.state = state;
        this.workspace = workspace;
        this.hideMenu = hideMenu;
        this.addView = addView;
        this.refreshPresentation = refreshPresentation;
        this.save = save;
        this.status = status;
        this.keyHandler = this::handleKeyPressed;
        this.windowHiddenHandler = event -> this.clipboardService.clear();

        installMouseTracking();
        installSceneKeyHandler();
    }

    public void clear() {
        clipboardService.clear();
    }

    private void installMouseTracking() {
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_ENTERED,
                this::updateMousePosition
        );
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_EXITED,
                event -> mouseInsideWorkspace = false
        );
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_MOVED,
                this::updateMousePosition
        );
    }

    private void updateMousePosition(MouseEvent event) {
        Point2D point = workspace.node().sceneToLocal(
                event.getSceneX(),
                event.getSceneY()
        );
        lastMouseX = point.getX();
        lastMouseY = point.getY();
        mouseInsideWorkspace = true;
    }

    private void installSceneKeyHandler() {
        workspace.node().sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
                    if (oldScene != null) {
                        oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                        oldScene.removeEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
                    }

                    if (newScene != null) {
                        newScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                        newScene.addEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
                    }
                }
        );
    }

    private void handleKeyPressed(KeyEvent event) {
        if (isTextInputTarget(event.getTarget()) || !event.isControlDown()) {
            return;
        }

        if (event.getCode() == KeyCode.C) {
            if (copySelection()) {
                event.consume();
            }
            return;
        }

        if (event.getCode() == KeyCode.V && pasteAtMouse()) {
            event.consume();
        }
    }

    private boolean copySelection() {
        Set<String> selectedIds = new LinkedHashSet<>(
                state.selectedNeuronIds()
        );

        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }

        if (selectedIds.isEmpty()) {
            clipboardService.clear();
            status.accept("Немає вибраного нейрона для копіювання.");
            return false;
        }

        if (!clipboardService.copy(selectedIds)) {
            return false;
        }

        NeuronClipboardService.ClipboardContent content =
                clipboardService.content().orElseThrow();

        status.accept(
                content.grouped()
                        ? "Групу нейронів скопійовано."
                        : content.items().size() == 1
                        ? "Нейрон скопійовано."
                        : "Вибрані нейрони скопійовано."
        );
        return true;
    }

    private boolean pasteAtMouse() {
        if (!mouseInsideWorkspace || !clipboardService.hasContent()) {
            return false;
        }

        double worldX = (lastMouseX - state.panX()) / state.zoom();
        double worldY = (lastMouseY - state.panY()) / state.zoom();

        hideMenu.run();
        state.clearSelection();

        List<Neuron> pasted = clipboardService.paste(
                worldX - NeuronView.WIDTH / 2.0,
                worldY - NeuronView.HEIGHT / 2.0
        );

        for (Neuron neuron : pasted) {
            addView.accept(neuron);
        }

        state.clearSelection();
        pasted.forEach(neuron -> state.toggleSelection(neuron.id()));

        refreshPresentation.run();
        save.run();

        status.accept(
                pasted.size() == 1
                        ? "Нейрон вставлено."
                        : "Нейрони вставлено."
        );
        return !pasted.isEmpty();
    }

    private boolean isTextInputTarget(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node instanceof TextInputControl) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}
