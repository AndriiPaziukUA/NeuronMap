
package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.NeuronVisualGeometry;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConnectionController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;

    private Line previewLine;
    private double lastCursorX;
    private double lastCursorY;

    /**
     * Compatibility constructor for existing callers/tests that still depend
     * on the application facade. New code should pass ConnectionService directly.
     */
    public ConnectionController(
            com.example.neuronmap.application.NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                Objects.requireNonNull(application, "application").connections(),
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status
        );
    }

    public ConnectionController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status
    ) {
        this.connectionService = connectionService;
        this.state = state;
        this.workspace = workspace;
        this.neuronViews = neuronViews;
        this.refresh = refresh;
        this.save = save;
        this.status = status;
    }

    public void install() {
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_MOVED,
                this::handleMouseMoved
        );

        workspace.node().addEventFilter(
                MouseEvent.MOUSE_CLICKED,
                this::handleCanvasClick
        );
    }

    public void beginCreate(String sourceNeuronId) {
        state.enterCreateConnectionMode(sourceNeuronId);

        previewLine = new Line();
        previewLine.setStroke(
                Color.web("#c5ccd6")
        );
        previewLine.setStrokeWidth(2.2);
        previewLine.getStrokeDashArray().setAll(
                9.0,
                7.0
        );
        previewLine.setMouseTransparent(true);

        workspace.overlayLayer()
                .getChildren()
                .add(previewLine);

        updatePreviewLine(
                lastCursorX,
                lastCursorY
        );

        workspace.node().setCursor(
                Cursor.CROSSHAIR
        );
        status.accept(
                "Клацни по іншому нейрону, щоб створити зв'язок."
        );
    }

    public void cancelCreate() {
        if (previewLine != null) {
            workspace.overlayLayer()
                    .getChildren()
                    .remove(previewLine);
        }

        previewLine = null;

        if (state.mode() == EditorState.Mode.CREATE_CONNECTION) {
            state.resetToIdle();
        }

        workspace.node().setCursor(
                Cursor.DEFAULT
        );
    }

    public void beginDelete(String sourceNeuronId) {
        if (!hasConnections(sourceNeuronId)) {
            exitDelete();
            status.accept(
                    "У цього нейрона немає зв'язків для видалення."
            );
            return;
        }

        state.enterDeleteConnectionMode(sourceNeuronId);
        refreshDeleteHighlights();
        workspace.node().setCursor(
                Cursor.CROSSHAIR
        );
        status.accept(
                "Клацни по нейрону або по лінії зв'язку, який треба видалити."
                        + " Права кнопка миші скасовує режим."
        );
    }

    /**
     * Returns whether the neuron participates in at least one connection.
     */
    public boolean hasConnections(String neuronId) {
        return connectionService.hasConnections(neuronId);
    }

    public void exitDelete() {
        state.resetToIdle();
        clearDeleteHighlights();
        workspace.node().setCursor(
                Cursor.DEFAULT
        );
    }

    public void refreshDeleteHighlights() {
        String sourceId =
                state.deleteConnectionNeuronId();

        if (state.mode() == EditorState.Mode.DELETE_CONNECTION
                && !hasConnections(sourceId)) {
            exitDelete();
            return;
        }

        for (Node node : workspace.edgeLayer().getChildren()) {
            if (!(node instanceof ConnectionView connectionView)) {
                continue;
            }

            Connection connection =
                    connectionView.model();

            boolean highlighted =
                    sourceId != null
                            && (connection.sourceId()
                            .equals(sourceId)
                            || connection.targetId()
                            .equals(sourceId));

            connectionView.setDeleteHighlight(
                    highlighted
            );
        }
    }

    public void clearDeleteHighlights() {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView) {
                connectionView.stopDeleteHighlight();
            }
        }
    }

    public void refreshPreviewAfterCameraChange() {
        updatePreviewLine(
                lastCursorX,
                lastCursorY
        );
    }

    private void handleMouseMoved(MouseEvent event) {
        lastCursorX = event.getX();
        lastCursorY = event.getY();
        updatePreviewLine(
                event.getX(),
                event.getY()
        );
    }

    private void handleCanvasClick(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (state.mode() == EditorState.Mode.DELETE_CONNECTION) {
                exitDelete();
                status.accept(
                        "Режим видалення зв'язків вимкнено."
                );
                event.consume();
            }
            return;
        }

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (state.mode() == EditorState.Mode.CREATE_CONNECTION) {
            handleCreateClick(event);
            return;
        }

        if (state.mode() == EditorState.Mode.DELETE_CONNECTION) {
            handleDeleteClick(event);
        }
    }

    private void handleCreateClick(MouseEvent event) {
        NeuronView target =
                findNeuronView(event.getTarget());

        if (target == null) {
            cancelCreate();
            status.accept(
                    "Створення зв'язку скасовано."
            );
            event.consume();
            return;
        }

        String sourceId =
                state.connectionSourceId();
        String targetId =
                target.model().id();

        if (sourceId.equals(targetId)) {
            status.accept(
                    "Не можна з'єднати нейрон із самим собою."
            );
            event.consume();
            return;
        }

        if (connectionService.create(
                sourceId,
                targetId
        )) {
            refresh.run();
            save.run();
            status.accept("Зв'язок створено.");
        } else {
            status.accept(
                    "Такий спрямований зв'язок уже існує."
            );
        }

        cancelCreate();
        event.consume();
    }

    private void handleDeleteClick(MouseEvent event) {
        NeuronView target =
                findNeuronView(event.getTarget());

        if (target != null) {
            String sourceId =
                    state.deleteConnectionNeuronId();
            String targetId =
                    target.model().id();

            if (sourceId.equals(targetId)) {
                status.accept(
                        "Не можна видалити самозв'язок."
                );
                event.consume();
                return;
            }

            int removed =
                    connectionService.removeBetween(
                            sourceId,
                            targetId
                    );

            if (removed > 0) {
                refresh.run();
                save.run();
                status.accept(
                        "Контакт між нейронами розірвано."
                );
            } else {
                status.accept(
                        "Між цими нейронами немає зв'язку."
                );
            }

            event.consume();
            return;
        }

        ConnectionView connectionView =
                findConnectionView(event.getTarget());

        if (connectionView == null) {
            connectionView = findConnectionViewAt(
                    event.getSceneX(),
                    event.getSceneY()
            );
        }

        if (connectionView != null
                && connectionView.isDeleteHighlighted()) {
            if (connectionService.remove(
                    connectionView.model().id()
            )) {
                refresh.run();
                save.run();
                status.accept("Зв'язок видалено.");
            }
            event.consume();
            return;
        }

        /*
         * ВАЖЛИВО: лівий клік по пустому місцю більше НЕ скасовує
         * режим видалення. Скасування виконується тільки secondary click.
         */
        event.consume();
    }

    private ConnectionView findConnectionViewAt(
            double sceneX,
            double sceneY
    ) {
        Point2D scenePoint = new Point2D(sceneX, sceneY);

        for (int index = workspace.edgeLayer().getChildren().size() - 1;
             index >= 0;
             index--) {
            Node node = workspace.edgeLayer()
                    .getChildren()
                    .get(index);

            if (!(node instanceof ConnectionView connectionView)) {
                continue;
            }

            if (containsVisibleShape(
                    connectionView,
                    scenePoint
            )) {
                return connectionView;
            }
        }

        return null;
    }

    /**
     * Tests the exact rendered geometry of a connection instead of relying
     * only on JavaFX's event target. This keeps the clickable area aligned
     * with the line/arrow that is actually drawn on screen.
     */
    private static boolean containsVisibleShape(
            Node node,
            Point2D scenePoint
    ) {
        if (!node.isVisible()) {
            return false;
        }

        if (node instanceof Shape shape) {
            Point2D localPoint = shape.sceneToLocal(scenePoint);

            if (shape.contains(localPoint)) {
                return true;
            }
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsVisibleShape(child, scenePoint)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void updatePreviewLine(
            double screenX,
            double screenY
    ) {
        if (previewLine == null
                || state.connectionSourceId() == null) {
            return;
        }

        NeuronView source =
                neuronViews.get(
                        state.connectionSourceId()
                );

        if (source == null) {
            cancelCreate();
            return;
        }

        /*
         * The output side is direction-dependent. Using WIDTH here always
         * starts the preview from the right side, which is wrong after a
         * reversed neuron. outputTipX() returns the actual visible output-tip
         * coordinate for the current direction.
         */
        double outputX = NeuronVisualGeometry.outputTipX(
                source.isDirectionReversed()
        );

        Point2D startScene =
                source.localToScene(
                        outputX,
                        NeuronView.HEIGHT / 2.0
                );

        Point2D start =
                workspace.node().sceneToLocal(
                        startScene
                );

        previewLine.setStartX(start.getX());
        previewLine.setStartY(start.getY());
        previewLine.setEndX(screenX);
        previewLine.setEndY(screenY);
    }

    private static NeuronView findNeuronView(Object target) {
        Node node = target instanceof Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (node instanceof NeuronView neuronView) {
                return neuronView;
            }
            node = node.getParent();
        }

        return null;
    }

    private static ConnectionView findConnectionView(Object target) {
        Node node = target instanceof Node targetNode
                ? targetNode
                : null;

        while (node != null) {
            if (node instanceof ConnectionView connectionView) {
                return connectionView;
            }
            node = node.getParent();
        }

        return null;
    }
}
