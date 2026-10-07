package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.application.clipboard.NeuronClipboard;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
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

/** Coordinates the in-memory neuron clipboard and Ctrl+C/Ctrl+V actions. */
public final class NeuronClipboardController {

    private final NeuronMapApplicationService application;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Consumer<Neuron> addView;
    private final Runnable refreshPresentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final NeuronClipboard clipboard = new NeuronClipboard();

    private final EventHandler<KeyEvent> keyHandler =
            this::handleKeyPressed;
    private final EventHandler<WindowEvent> windowHiddenHandler =
            event -> clipboard.clear();

    private boolean mouseInsideWorkspace;
    private double lastMouseX;
    private double lastMouseY;

    public NeuronClipboardController(
            NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<Neuron> addView,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status
    ) {
        this.application = application;
        this.state = state;
        this.workspace = workspace;
        this.hideMenu = hideMenu;
        this.addView = addView;
        this.refreshPresentation = refreshPresentation;
        this.save = save;
        this.status = status;

        installMouseTracking();
        installSceneKeyHandler();
    }

    public void clear() {
        clipboard.clear();
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
                        oldScene.removeEventFilter(
                                KeyEvent.KEY_PRESSED,
                                keyHandler
                        );
                        oldScene.removeEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
                    }

                    if (newScene != null) {
                        newScene.addEventFilter(
                                KeyEvent.KEY_PRESSED,
                                keyHandler
                        );
                        newScene.addEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
                    }
                }
        );
    }

    private void handleKeyPressed(KeyEvent event) {
        if (isTextInputTarget(event.getTarget())) {
            return;
        }

        if (!event.isControlDown()) {
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

    private boolean isTextInputTarget(Object target) {
        Node node = target instanceof Node targetNode
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
            clipboard.clear();
            status.accept("Немає вибраного нейрона для копіювання.");
            return false;
        }

        boolean grouped = false;
        Set<String> idsToCopy;

        if (selectedIds.size() == 1) {
            String neuronId = selectedIds.iterator().next();
            NeuronGroup group = application.model().groupContaining(neuronId);
            if (group != null) {
                idsToCopy = new LinkedHashSet<>(group.memberIds());
                grouped = true;
            } else {
                idsToCopy = new LinkedHashSet<>(selectedIds);
            }
        } else {
            idsToCopy = new LinkedHashSet<>(selectedIds);
            Set<String> idsForGroupCheck = Set.copyOf(idsToCopy);
            grouped = application.model().groups().stream()
                    .anyMatch(group ->
                            group.memberIds().equals(idsForGroupCheck)
                    );
        }

        Set<String> copiedIds = Set.copyOf(idsToCopy);

        List<Neuron> neurons = copiedIds.stream()
                .map(application.model()::neuron)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (neurons.isEmpty()) {
            clipboard.clear();
            return false;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Neuron neuron : neurons) {
            NeuronPresentation presentation =
                    application.model().presentation(neuron.id());
            if (presentation == null) {
                continue;
            }

            double centerX = presentation.x() + NeuronView.WIDTH / 2.0;
            double centerY = presentation.y() + NeuronView.HEIGHT / 2.0;
            minX = Math.min(minX, centerX);
            maxX = Math.max(maxX, centerX);
            minY = Math.min(minY, centerY);
            maxY = Math.max(maxY, centerY);
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY)) {
            clipboard.clear();
            return false;
        }

        double anchorX = (minX + maxX) / 2.0;
        double anchorY = (minY + maxY) / 2.0;

        List<NeuronClipboard.NeuronData> items = neurons.stream()
                .map(neuron -> {
                    NeuronPresentation presentation =
                            application.model().presentation(neuron.id());
                    double centerX = presentation.x() + NeuronView.WIDTH / 2.0;
                    double centerY = presentation.y() + NeuronView.HEIGHT / 2.0;

                    return new NeuronClipboard.NeuronData(
                            neuron.type(),
                            neuron.signalStrength(),
                            neuron.activationThreshold(),
                            centerX - anchorX,
                            centerY - anchorY,
                            presentation.rotationDegrees(),
                            presentation.directionReversed()
                    );
                })
                .toList();

        clipboard.copy(items, grouped);
        status.accept(
                grouped
                        ? "Групу нейронів скопійовано."
                        : neurons.size() == 1
                        ? "Нейрон скопійовано."
                        : "Вибрані нейрони скопійовано."
        );
        return true;
    }

    private boolean pasteAtMouse() {
        if (!mouseInsideWorkspace || !clipboard.hasContent()) {
            return false;
        }

        NeuronClipboard.ClipboardContent content = clipboard.content();
        double worldX = (lastMouseX - state.panX()) / state.zoom();
        double worldY = (lastMouseY - state.panY()) / state.zoom();

        hideMenu.run();
        state.clearSelection();

        LinkedHashSet<String> pastedIds = new LinkedHashSet<>();

        for (NeuronClipboard.NeuronData item : content.items()) {
            double centerX = worldX + item.offsetX();
            double centerY = worldY + item.offsetY();

            Neuron neuron = application.createNeuron(
                    item.type(),
                    centerX - NeuronView.WIDTH / 2.0,
                    centerY - NeuronView.HEIGHT / 2.0
            );

            neuron.setSignalStrength(item.signalStrength());
            neuron.setActivationThreshold(item.activationThreshold());

            NeuronPresentation presentation =
                    application.model().presentation(neuron.id());
            presentation.setRotationDegrees(item.rotationDegrees());
            presentation.setDirectionReversed(item.directionReversed());

            addView.accept(neuron);
            pastedIds.add(neuron.id());
        }

        if (content.grouped() && pastedIds.size() >= 2) {
            application.createGroup(pastedIds);
        }

        state.clearSelection();
        pastedIds.forEach(state::toggleSelection);

        refreshPresentation.run();
        save.run();

        status.accept(
                pastedIds.size() == 1
                        ? "Нейрон вставлено."
                        : "Нейрони вставлено."
        );
        return !pastedIds.isEmpty();
    }
}
