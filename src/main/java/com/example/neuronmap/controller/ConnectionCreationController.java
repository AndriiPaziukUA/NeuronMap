package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.NeuronVisualGeometry;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns only the create-connection mode and its preview line. */
final class ConnectionCreationController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;
    private final Runnable exitDelete;

    private Line previewLine;
    private double lastCursorX;
    private double lastCursorY;

    ConnectionCreationController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            Runnable exitDelete
    ) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.exitDelete = Objects.requireNonNull(exitDelete, "exitDelete");
    }

    void beginCreate(String sourceNeuronId) {
        exitDelete.run();
        state.enterCreateConnectionMode(sourceNeuronId);

        previewLine = new Line();
        previewLine.setStroke(Color.web("#c5ccd6"));
        previewLine.setStrokeWidth(2.2);
        previewLine.getStrokeDashArray().setAll(9.0, 7.0);
        previewLine.setMouseTransparent(true);

        workspace.overlayLayer().getChildren().add(previewLine);
        updatePreviewLine(lastCursorX, lastCursorY);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept("Клацни по іншому нейрону, щоб створити зв'язок.");
    }

    void cancelCreate() {
        if (previewLine != null) {
            workspace.overlayLayer().getChildren().remove(previewLine);
        }

        previewLine = null;

        if (state.mode() == EditorState.Mode.CREATE_CONNECTION) {
            state.resetToIdle();
        }

        workspace.node().setCursor(Cursor.DEFAULT);
    }

    void handleMouseMoved(MouseEvent event) {
        lastCursorX = event.getX();
        lastCursorY = event.getY();
        updatePreviewLine(event.getX(), event.getY());
    }

    void refreshPreviewAfterCameraChange() {
        updatePreviewLine(lastCursorX, lastCursorY);
    }

    void handleCreateClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        NeuronView target = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (target == null) {
            cancelCreate();
            status.accept("Створення зв'язку скасовано.");
            event.consume();
            return;
        }

        String sourceId = state.connectionSourceId();
        String targetId = target.model().id();

        if (sourceId == null || sourceId.equals(targetId)) {
            status.accept("Не можна з'єднати нейрон із самим собою.");
            event.consume();
            return;
        }

        if (connectionService.create(sourceId, targetId)) {
            refresh.run();
            save.run();
            status.accept("Зв'язок створено.");
        } else {
            status.accept("Такий спрямований зв'язок уже існує.");
        }

        cancelCreate();
        event.consume();
    }

    private void updatePreviewLine(double screenX, double screenY) {
        if (previewLine == null || state.connectionSourceId() == null) {
            return;
        }

        NeuronView source = neuronViews.get(state.connectionSourceId());
        if (source == null) {
            cancelCreate();
            return;
        }

        double outputX = NeuronVisualGeometry.outputTipX(
                source.isDirectionReversed()
        );

        Point2D startScene = source.localToScene(
                outputX,
                NeuronView.HEIGHT / 2.0
        );
        Point2D start = workspace.node().sceneToLocal(startScene);

        previewLine.setStartX(start.getX());
        previewLine.setStartY(start.getY());
        previewLine.setEndX(screenX);
        previewLine.setEndY(screenY);
    }

}
