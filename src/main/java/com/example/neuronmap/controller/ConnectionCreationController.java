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
 * Обробляє створення зв’язку: відстежує нейрон-джерело, показує попередню лінію та завершує створення після клацання по цілі.
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
     * Створює екземпляр ConnectionCreationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refresh callback для оновлення інтерфейсу після зміни моделі.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
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
     * Створює екземпляр ConnectionCreationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param refresh callback для оновлення інтерфейсу після зміни моделі.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param exitDelete callback, який завершує режим видалення зв’язку.
     * @param localization служба локалізації інтерфейсу.
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
     * Починає операцію create та готує стан взаємодії.
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
     * Перевіряє, чи дозволяє поточний стан виконати cel create.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
     * Переміщує кінець попередньої лінії створення зв’язку за курсором.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
     */
    void handleMouseMoved(MouseEvent event) {
        lastCursorX = event.getX();
        lastCursorY = event.getY();
        updatePreviewLine(event.getX(), event.getY());
    }

    /**
     * Оновлює геометрію тимчасової лінії зв’язку після зміни камери.
     */
    void refreshPreviewAfterCameraChange() {
        updatePreviewLine(lastCursorX, lastCursorY);
    }

    /**
     * Обробляє клацання під час створення зв’язку та перевіряє вибрану ціль.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
     * Оновлює кінцеву точку тимчасової лінії до координат курсора.
     *
     * @param screenX горизонтальна екранна координата.
     * @param screenY вертикальна екранна координата.
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
