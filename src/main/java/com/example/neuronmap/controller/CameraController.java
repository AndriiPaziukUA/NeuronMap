package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.util.NumberDisplayFormatter;
import com.example.neuronmap.util.ViewportWorldCoordinates;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Обробляє панорамування й масштабування полотна, координати вказівника та відкладене збереження камери. */
public final class CameraController {

    private static final double SAVE_DEBOUNCE_MILLIS = 250.0;

    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable refreshOverlay;
    private final Runnable save;
    private final BooleanSupplier interactionActive;
    private final Predicate<Object> interactiveTarget;
    private final Consumer<String> status;
    private final Consumer<Point2D> cursorWorldConsumer;
    private final LocalizationService localization;
    private final double zoomFactor;
    private final PauseTransition saveDebounce =
            new PauseTransition(Duration.millis(SAVE_DEBOUNCE_MILLIS));

    private final EventHandler<MouseEvent> scenePointerMovedHandler = this::handleScenePointerEvent;
    private final EventHandler<MouseEvent> scenePointerDraggedHandler = this::handleScenePointerEvent;
    private final EventHandler<MouseEvent> scenePointerExitedHandler = this::handleScenePointerExited;
    private final EventHandler<MouseEvent> workspacePointerExitedHandler = this::handlePointerExited;
    private final EventHandler<MouseEvent> workspacePressedHandler = this::handlePressed;
    private final EventHandler<MouseEvent> workspaceDraggedHandler = this::handleDragged;
    private final EventHandler<MouseEvent> workspaceReleasedHandler = this::handleReleased;
    private final EventHandler<ScrollEvent> workspaceZoomHandler = this::handleZoom;
    private final ChangeListener<Scene> sceneChangeListener = this::handleSceneChanged;
    private final ChangeListener<Number> widthChangeListener =
            (observable, oldValue, newValue) -> refreshCursorCoordinates();
    private final ChangeListener<Number> heightChangeListener =
            (observable, oldValue, newValue) -> refreshCursorCoordinates();

    private Scene pointerTrackingScene;
    private boolean installed;
    private boolean disposed;
    private boolean panning;
    private boolean suspended;
    private boolean cameraSavePending;
    private boolean cursorInsideWorkspace;
    private double cursorSceneX;
    private double cursorSceneY;
    private double lastMouseX;
    private double lastMouseY;
    private double panStartX;
    private double panStartY;

    /**
     * Створює контролер камери та налаштовує його залежності.
     *
     * @param state змінний стан камери редактора.
     * @param workspace полотно редактора.
     * @param refreshOverlay дія оновлення положення накладок.
     * @param save дія збереження стану редактора.
     * @param interactionActive перевірка активної взаємодії з полотном.
     * @param interactiveTarget перевірка інтерактивної цілі події.
     * @param status дія показу повідомлення в рядку стану.
     * @param cursorWorldConsumer отримувач координат указівника у світовій системі.
     * @param zoomFactor коефіцієнт зміни масштабу за один крок прокручування.
     * @param localization обов'язкова спільна служба локалізації інтерфейсу.
     * @throws NullPointerException якщо локалізацію не передано.
     * @throws IllegalArgumentException якщо коефіцієнт масштабу не є скінченним числом, більшим за одиницю.
     */
    public CameraController(
            EditorState state,
            WorkspaceView workspace,
            Runnable refreshOverlay,
            Runnable save,
            BooleanSupplier interactionActive,
            Predicate<Object> interactiveTarget,
            Consumer<String> status,
            Consumer<Point2D> cursorWorldConsumer,
            double zoomFactor,
            LocalizationService localization
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.state = state;
        this.workspace = workspace;
        this.refreshOverlay = refreshOverlay;
        this.save = save;
        this.interactionActive = interactionActive;
        this.interactiveTarget = interactiveTarget;
        this.status = status;
        this.cursorWorldConsumer = cursorWorldConsumer;
        if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
            throw new IllegalArgumentException("zoomFactor must be > 1");
        }
        this.zoomFactor = zoomFactor;
        saveDebounce.setOnFinished(event -> savePendingCameraChanges());
    }

    /** Реєструє обробники камери та відстежує курсор на всій сцені. Повторний виклик безпечний. */
    public void install() {
        if (disposed) {
            throw new IllegalStateException("Camera controller is disposed");
        }
        if (installed) {
            return;
        }
        installed = true;
        workspace.node().sceneProperty().addListener(sceneChangeListener);
        attachPointerTracking(workspace.node().getScene());
        workspace.node().addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, workspacePointerExitedHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_PRESSED, workspacePressedHandler);
        workspace.node().addEventHandler(MouseEvent.MOUSE_DRAGGED, workspaceDraggedHandler);
        workspace.node().addEventHandler(MouseEvent.MOUSE_RELEASED, workspaceReleasedHandler);
        workspace.node().addEventHandler(ScrollEvent.SCROLL, workspaceZoomHandler);
        workspace.node().widthProperty().addListener(widthChangeListener);
        workspace.node().heightProperty().addListener(heightChangeListener);
    }

    /** Від'єднує всі реєстрації контролера та завершує його життєвий цикл; повторні виклики безпечні. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (installed) {
            workspace.node().sceneProperty().removeListener(sceneChangeListener);
            detachPointerTracking(pointerTrackingScene);
            pointerTrackingScene = null;
            workspace.node().removeEventFilter(MouseEvent.MOUSE_EXITED_TARGET, workspacePointerExitedHandler);
            workspace.node().removeEventFilter(MouseEvent.MOUSE_PRESSED, workspacePressedHandler);
            workspace.node().removeEventHandler(MouseEvent.MOUSE_DRAGGED, workspaceDraggedHandler);
            workspace.node().removeEventHandler(MouseEvent.MOUSE_RELEASED, workspaceReleasedHandler);
            workspace.node().removeEventHandler(ScrollEvent.SCROLL, workspaceZoomHandler);
            workspace.node().widthProperty().removeListener(widthChangeListener);
            workspace.node().heightProperty().removeListener(heightChangeListener);
            installed = false;
        }
        saveDebounce.stop();
        cameraSavePending = false;
        panning = false;
        suspended = false;
        cursorSceneX = 0.0;
        cursorSceneY = 0.0;
        workspace.node().setCursor(Cursor.DEFAULT);
        clearCursorCoordinates();
    }

    /** Під'єднує фільтри відстеження курсора до сцени, фактично прив'язаної до полотна. */
    private void attachPointerTracking(Scene scene) {
        if (scene == null || scene == pointerTrackingScene) {
            return;
        }
        detachPointerTracking(pointerTrackingScene);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, scenePointerMovedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, scenePointerMovedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, scenePointerDraggedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, scenePointerExitedHandler);
        pointerTrackingScene = scene;
    }

    /** Знімає всі фільтри відстеження курсора з конкретної сцени, а не з її можливого нового замінника. */
    private void detachPointerTracking(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_MOVED, scenePointerMovedHandler);
        scene.removeEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, scenePointerMovedHandler);
        scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, scenePointerDraggedHandler);
        scene.removeEventFilter(MouseEvent.MOUSE_EXITED, scenePointerExitedHandler);
    }

    /** Переносить глобальне відстеження курсора після заміни сцени полотна. */
    private void handleSceneChanged(
            javafx.beans.value.ObservableValue<? extends Scene> observable,
            Scene oldScene,
            Scene newScene
    ) {
        detachPointerTracking(pointerTrackingScene);
        pointerTrackingScene = null;
        attachPointerTracking(newScene);
        clearCursorCoordinates();
    }

    /**
     * Застосовує лише масштаб і зміщення, які не залежать від остаточного розміру вікна.
     * Цей крок можна виконати до показу Stage, щоб перший кадр уже мав збережений стан камери.
     */
    public void applyTransform() {
        workspace.setWorldTransform(state.zoom(), state.panX(), state.panY());
    }

    /**
     * Застосовує стан камери до полотна та оновлює залежні від компонування накладки й координати курсора.
     *
     * <p>Повне оновлення слід виконувати після компонування сцени, оскільки накладки можуть залежати
     * від доступних розмірів робочої області.</p>
     */
    public void apply() {
        applyTransform();
        refreshOverlay.run();
        refreshCursorCoordinates();
    }

    /** Призупиняє зміни камери перед відкриттям меню та завершує лише відкладене збереження реальних змін. */
    public void pausePendingSave() {
        if (suspended || disposed) {
            return;
        }
        suspended = true;
        clearCursorCoordinates();
        if (panning) {
            if (hasPanChanged()) {
                cameraSavePending = true;
            }
            panning = false;
            workspace.node().setCursor(Cursor.DEFAULT);
        }
        flushPendingCameraChanges();
    }

    /** Відновлює обробку змін камери після закриття меню, якщо контролер ще активний. */
    public void resumePendingSave() {
        if (!disposed) {
            suspended = false;
        }
    }

    /** Скасовує таймер збереження та скидає панорамування й тимчасовий курсор перетягування. */
    public void cancelPendingSave() {
        saveDebounce.stop();
        cameraSavePending = false;
        panning = false;
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /** Оновлює координати лише тоді, коли ціль події належить полотну; поза ним підпис очищується. */
    private void handleScenePointerEvent(MouseEvent event) {
        if (isWorkspaceTarget(event.getTarget())) {
            updateCursorCoordinates(event.getSceneX(), event.getSceneY());
        } else {
            clearCursorCoordinates();
        }
    }

    /** Очищає координати, коли курсор залишає кореневу область вікна. */
    private void handleScenePointerExited(MouseEvent event) {
        Scene scene = pointerTrackingScene;
        if (scene != null && event.getTarget() == scene.getRoot()) {
            clearCursorCoordinates();
        }
    }

    /** Очищає показник, коли вказівник залишає геометричні межі полотна. */
    private void handlePointerExited(MouseEvent event) {
        Point2D viewportPoint = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        if (event.getTarget() == workspace.node() || !isInsideWorkspace(viewportPoint)) {
            clearCursorCoordinates();
        }
    }

    /** Перевіряє, чи є ціль події самим полотном або одним із його дочірніх вузлів. */
    private boolean isWorkspaceTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (current == workspace.node()) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** Обробляє початок панорамування, не блокуючи його в режимах створення або видалення зв'язків. */
    private void handlePressed(MouseEvent event) {
        updateCursorCoordinates(event.getSceneX(), event.getSceneY());
        if (disposed || suspended
                || event.getButton() != MouseButton.PRIMARY
                || (interactionActive.getAsBoolean() && !connectionModeActive())
                || interactiveTarget.test(event.getTarget())) {
            return;
        }
        panning = true;
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        panStartX = state.panX();
        panStartY = state.panY();
        workspace.node().setCursor(Cursor.CLOSED_HAND);
        event.consume();
    }

    /** Оновлює зміщення камери лише за фактичної зміни координат вказівника. */
    private void handleDragged(MouseEvent event) {
        if (disposed || suspended || !panning) {
            updateCursorCoordinates(event.getSceneX(), event.getSceneY());
            return;
        }
        double deltaX = event.getX() - lastMouseX;
        double deltaY = event.getY() - lastMouseY;
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        if (deltaX == 0.0 && deltaY == 0.0) {
            updateCursorCoordinates(event.getSceneX(), event.getSceneY());
            event.consume();
            return;
        }
        state.setPanX(state.panX() + deltaX);
        state.setPanY(state.panY() + deltaY);
        apply();
        updateCursorCoordinates(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    /** Завершує панорамування та відновлює курсор активного режиму, зберігаючи відкладене збереження. */
    private void handleReleased(MouseEvent event) {
        if (disposed || suspended || !panning) {
            return;
        }
        boolean cameraMoved = hasPanChanged();
        panning = false;
        workspace.node().setCursor(connectionModeActive() ? Cursor.CROSSHAIR : Cursor.DEFAULT);
        if (cameraMoved) {
            scheduleSave();
        }
    }

    /** Повертає ознаку активності режимів зв'язків, у яких після панорамування потрібен курсор-приціл. */
    private boolean connectionModeActive() {
        return state.mode() == EditorState.Mode.CREATE_CONNECTION
                || state.mode() == EditorState.Mode.DELETE_CONNECTION;
    }

    /** Змінює масштаб із прив'язкою до позиції курсора та показує локалізоване повідомлення. */
    private void handleZoom(ScrollEvent event) {
        updateCursorCoordinates(event.getSceneX(), event.getSceneY());
        if (disposed || suspended || event.getDeltaY() == 0.0) {
            return;
        }
        Point2D cursor = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        double oldZoom = state.zoom();
        double factor = event.getDeltaY() > 0.0 ? zoomFactor : 1.0 / zoomFactor;
        double newZoom = GeometryUtils.clampZoom(oldZoom * factor);
        if (Double.compare(oldZoom, newZoom) == 0) {
            return;
        }
        double worldX = (cursor.getX() - state.panX()) / oldZoom;
        double worldY = (cursor.getY() - state.panY()) / oldZoom;
        state.setZoom(newZoom);
        state.setPanX(cursor.getX() - worldX * newZoom);
        state.setPanY(cursor.getY() - worldY * newZoom);
        apply();
        updateCursorCoordinates(event.getSceneX(), event.getSceneY());
        scheduleSave();
        status.accept(localization.text("status.scale", formatZoom(newZoom)));
        event.consume();
    }

    /** Зберігає поточні координати в локальному просторі сцени та оновлює їх світове значення. */
    private void updateCursorCoordinates(double sceneX, double sceneY) {
        Point2D viewportPoint = workspace.node().sceneToLocal(sceneX, sceneY);
        if (!isInsideWorkspace(viewportPoint)) {
            clearCursorCoordinates();
            return;
        }
        cursorInsideWorkspace = true;
        cursorSceneX = sceneX;
        cursorSceneY = sceneY;
        refreshCursorCoordinates();
    }

    /** Перераховує останню позицію вказівника після зміни масштабу, панорамування або розміру полотна. */
    private void refreshCursorCoordinates() {
        if (!cursorInsideWorkspace || cursorWorldConsumer == null || disposed) {
            return;
        }
        Point2D viewportPoint = workspace.node().sceneToLocal(cursorSceneX, cursorSceneY);
        if (!isInsideWorkspace(viewportPoint)) {
            clearCursorCoordinates();
            return;
        }
        cursorWorldConsumer.accept(ViewportWorldCoordinates.calculate(
                viewportPoint.getX(), viewportPoint.getY(), state.zoom(), state.panX(), state.panY()
        ));
    }

    /** Прибирає координати з панелі, коли вказівник залишає видиму область полотна. */
    private void clearCursorCoordinates() {
        if (!cursorInsideWorkspace) {
            return;
        }
        cursorInsideWorkspace = false;
        if (cursorWorldConsumer != null) {
            cursorWorldConsumer.accept(new Point2D(Double.NaN, Double.NaN));
        }
    }

    /** Перевіряє, чи розташована точка всередині актуальних меж видимого полотна. */
    private boolean isInsideWorkspace(Point2D viewportPoint) {
        return viewportPoint.getX() >= 0.0
                && viewportPoint.getY() >= 0.0
                && viewportPoint.getX() < workspace.node().getWidth()
                && viewportPoint.getY() < workspace.node().getHeight();
    }

    /** Перевіряє, чи змінилися координати камери від початку поточного перетягування. */
    private boolean hasPanChanged() {
        return Double.compare(state.panX(), panStartX) != 0
                || Double.compare(state.panY(), panStartY) != 0;
    }

    /** Планує збереження лише після фактичної зміни масштабу або зміщення камери. */
    private void scheduleSave() {
        if (disposed) {
            return;
        }
        cameraSavePending = true;
        saveDebounce.playFromStart();
    }

    /** Записує стан карти лише тоді, коли камера справді змінилася після останнього збереження. */
    private void savePendingCameraChanges() {
        if (disposed || !cameraSavePending) {
            return;
        }
        save.run();
        cameraSavePending = false;
    }

    /** Завершує відкладений запис перед призупиненням UI, не зберігаючи незмінений стан камери. */
    private void flushPendingCameraChanges() {
        saveDebounce.stop();
        if (!cameraSavePending) {
            return;
        }
        savePendingCameraChanges();
    }

    /**
     * Форматує масштаб як відсоток за спільними правилами відображення чисел.
     *
     * @param zoom коефіцієнт масштабу камери.
     * @return компактне представлення масштабу у відсотках.
     */
    private static String formatZoom(double zoom) {
        return NumberDisplayFormatter.formatPercentage(zoom * 100.0);
    }
}
