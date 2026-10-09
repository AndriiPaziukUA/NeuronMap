package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
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

/**
 * Організовує створення спрямованого звʼязку між початковим і кінцевим нейронами.
 */
final class ConnectionCreationController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;
    private final Runnable exitDelete;
    private final LocalizationService localization;

    private Line previewLine;
    private double lastCursorX;
    private double lastCursorY;

    /**
     * Створює обʼєкт ConnectionCreationController та ініціалізує його початковий стан.
     *
     * @param connectionService значення, що визначає звʼязок служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param refresh значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     */
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
        this(
                connectionService,
                state,
                workspace,
                neuronViews,
                refresh,
                save,
                status,
                exitDelete,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює обʼєкт ConnectionCreationController та ініціалізує його початковий стан.
     *
     * @param connectionService значення, що визначає звʼязок служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param refresh значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param exitDelete значення, що визначає видалити для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     */
    ConnectionCreationController(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            Runnable exitDelete,
            LocalizationService localization
    ) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.exitDelete = Objects.requireNonNull(exitDelete, "exitDelete");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Запускає або планує дію, повʼязану з «створити».
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
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
        status.accept(localization.text("status.connection_create_hint"));
    }

    /**
     * Завершує або скасовує дію, повʼязану з «створити».
     */
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

    /**
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
     */
    void handleMouseMoved(MouseEvent event) {
        lastCursorX = event.getX();
        lastCursorY = event.getY();
        updatePreviewLine(event.getX(), event.getY());
    }

    /**
     * Обробляє «попередній перегляд після камера змінити».
     */
    void refreshPreviewAfterCameraChange() {
        updatePreviewLine(lastCursorX, lastCursorY);
    }

    /**
     * Обробляє «створити».
     *
     * @param event подія інтерфейсу.
     */
    void handleCreateClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        NeuronView target = JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class);
        if (target == null) {
            cancelCreate();
            status.accept(localization.text("status.connection_create_cancelled"));
            event.consume();
            return;
        }

        String sourceId = state.connectionSourceId();
        String targetId = target.model().id();

        if (sourceId == null || sourceId.equals(targetId)) {
            status.accept(localization.text("status.connection_self_forbidden"));
            event.consume();
            return;
        }

        if (connectionService.create(sourceId, targetId)) {
            refresh.run();
            save.run();
            status.accept(localization.text("status.connection_created"));
        } else {
            status.accept(localization.text("status.connection_exists"));
        }

        cancelCreate();
        event.consume();
    }

    /**
     * Задає або оновлює значення, повʼязані з «попередній перегляд».
     *
     * @param screenX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param screenY значення, що визначає відповідну операцію для цієї операції.
     */
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
