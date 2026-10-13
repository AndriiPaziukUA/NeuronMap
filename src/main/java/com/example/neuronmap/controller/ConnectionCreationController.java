package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.ConnectionPreviewView;
import com.example.neuronmap.view.JavaFxNodeLookup;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Обробляє створення зв’язку: відстежує нейрон-джерело, передає координати курсора поданню прев’ю та завершує операцію після клацання по цілі.
 */
final class ConnectionCreationController {

    private final ConnectionService connectionService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final ConnectionPreviewView previewView;
    private final Runnable refresh;
    private final Runnable save;
    private final Consumer<String> status;
    private final Runnable exitDelete;
    private final LocalizationService localization;

    private double lastCursorX;
    private double lastCursorY;

    /**
     * Створює контролер створення зв’язків і готує окреме подання тимчасової лінії.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state стан режиму взаємодії редактора.
     * @param workspace полотно редактора.
     * @param neuronViews подання нейронів за їхніми ідентифікаторами.
     * @param refresh callback оновлення інтерфейсу після зміни моделі.
     * @param save callback збереження змін.
     * @param status callback показу повідомлення в рядку стану.
     * @param exitDelete callback завершення режиму видалення зв’язків.
     * @param localization служба перекладів інтерфейсу.
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
        this.previewView = new ConnectionPreviewView(workspace);
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.exitDelete = Objects.requireNonNull(exitDelete, "exitDelete");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Починає режим створення зв’язку та показує прев’ю від вихідного порту вибраного нейрона.
     *
     * @param sourceNeuronId ідентифікатор початкового нейрона.
     */
    void beginCreate(String sourceNeuronId) {
        exitDelete.run();
        state.enterCreateConnectionMode(sourceNeuronId);

        NeuronView source = neuronViews.get(sourceNeuronId);
        if (source == null) {
            cancelCreate();
            status.accept(localization.text("status.connection_create_cancelled"));
            return;
        }

        previewView.show(source, cursorPosition());
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(localization.text("status.connection_create_hint"));
    }

    /**
     * Скасовує незавершене створення зв’язку та доручає поданню прибрати графічне прев’ю.
     */
    void cancelCreate() {
        previewView.hide();

        if (state.mode() == EditorState.Mode.CREATE_CONNECTION) {
            state.resetToIdle();
        }

        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /**
     * Запам’ятовує позицію курсора та передає поданню команду оновити кінець прев’ю.
     *
     * @param event подія переміщення миші.
     */
    void handleMouseMoved(MouseEvent event) {
        lastCursorX = event.getX();
        lastCursorY = event.getY();
        updatePreview(lastCursorX, lastCursorY);
    }

    /**
     * Оновлює прев’ю після зміни камери, повторно передаючи поточне джерело та останню позицію курсора.
     */
    void refreshPreviewAfterCameraChange() {
        updatePreview(lastCursorX, lastCursorY);
    }

    /**
     * Обробляє клацання під час створення зв’язку та перевіряє вибрану ціль.
     *
     * @param event подія клацання миші.
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
     * Передає поданню чинний нейрон-джерело й координати курсора, якщо режим створення ще активний.
     * Якщо подання джерела вже відсутнє, завершує незавершену операцію.
     *
     * @param cursorX горизонтальна координата курсора в системі координат полотна.
     * @param cursorY вертикальна координата курсора в системі координат полотна.
     */
    private void updatePreview(double cursorX, double cursorY) {
        if (state.mode() != EditorState.Mode.CREATE_CONNECTION
                || state.connectionSourceId() == null) {
            return;
        }

        NeuronView source = neuronViews.get(state.connectionSourceId());
        if (source == null) {
            cancelCreate();
            return;
        }

        previewView.update(source, new Point2D(cursorX, cursorY));
    }

    /**
     * Формує точку з останньої збереженої позиції курсора в координатах полотна.
     *
     * @return остання позиція курсора.
     */
    private Point2D cursorPosition() {
        return new Point2D(lastCursorX, lastCursorY);
    }
}
