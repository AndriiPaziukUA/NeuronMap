package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.util.WorldCoordinateMapper;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronDragPreviewFactory;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Scale;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Керує drag-and-drop інструментів нейронів і тривалим режимом розміщення під курсором. */
public final class NeuronToolDragController {

    private static final String ACTIVE_TOOL_STYLE_CLASS = "tool-button-active";

    private final EditorState state;
    private final WorkspaceView workspace;
    private final BiConsumer<NeuronType, Point2D> dropConsumer;
    private final EventHandler<DragEvent> dragOverHandler = this::handleDragOver;
    private final EventHandler<DragEvent> dragExitedHandler = this::handleDragExited;
    private final EventHandler<DragEvent> droppedHandler = this::handleDrop;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final EventHandler<MouseEvent> mouseExitedHandler = this::handleMouseExited;
    private final Map<Button, DragSourceRegistration> dragSources = new IdentityHashMap<>();
    private final Map<NeuronType, Button> toolbarButtons = new EnumMap<>(NeuronType.class);

    private Node dragPreview;
    private NeuronType dragPreviewType;
    private Scale dragPreviewScale;
    private NeuronType activePlacementType;
    private double lastPointerSceneX;
    private double lastPointerSceneY;
    private boolean hasLastPointerPosition;
    private boolean installed;
    private boolean disposed;

    /**
     * Створює контролер перетягування інструментів нейронів.
     *
     * @param state стан редактора, що містить параметри камери.
     * @param workspace полотно редактора.
     * @param dropConsumer callback, який отримує тип нейрона та координату центра у світових координатах.
     * @throws NullPointerException якщо залежність не задано.
     */
    public NeuronToolDragController(
            EditorState state,
            WorkspaceView workspace,
            BiConsumer<NeuronType, Point2D> dropConsumer
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.dropConsumer = Objects.requireNonNull(dropConsumer, "dropConsumer");
    }

    /**
     * Реєструє обробники полотна; повторна установка не дублює підписки.
     *
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     */
    public void install() {
        ensureNotDisposed();
        if (installed) {
            return;
        }
        workspace.node().addEventHandler(DragEvent.DRAG_OVER, dragOverHandler);
        workspace.node().addEventHandler(DragEvent.DRAG_EXITED, dragExitedHandler);
        workspace.node().addEventHandler(DragEvent.DRAG_DROPPED, droppedHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, mouseExitedHandler);
        installed = true;
    }

    /**
     * Налаштовує наявні кнопки панелі як джерела перетягування.
     * Цей перевантажений метод зберігає сумісність з поточною панеллю з двома типами нейронів.
     *
     * @param excitatoryButton кнопка інструмента збуджувального нейрона.
     * @param inhibitoryButton кнопка інструмента гальмівного нейрона.
     */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        ensureNotDisposed();
        if (excitatoryButton != null) {
            registerToolbarButton(excitatoryButton, NeuronType.EXCITATORY);
        }
        if (inhibitoryButton != null) {
            registerToolbarButton(inhibitoryButton, NeuronType.INHIBITORY);
        }
    }

    /**
     * Налаштовує довільну кількість кнопок інструментів за відповідністю типу нейрона та кнопки.
     *
     * @param buttons відповідність типів нейронів кнопкам панелі інструментів.
     * @throws NullPointerException якщо карта, тип або кнопка не задані.
     * @throws IllegalArgumentException якщо тип або кнопку вже пов'язано з іншою реєстрацією.
     */
    public void configureToolbarButtons(Map<NeuronType, Button> buttons) {
        ensureNotDisposed();
        Objects.requireNonNull(buttons, "buttons");
        buttons.forEach((type, button) -> registerToolbarButton(button, type));
    }

    /**
     * Реєструє одну кнопку інструмента, не створюючи обробників повторно.
     *
     * @param button кнопка інструмента.
     * @param type тип нейрона, який створює інструмент.
     * @throws NullPointerException якщо кнопку або тип не задано.
     * @throws IllegalArgumentException якщо тип або кнопку вже пов'язано з іншою реєстрацією.
     */
    public void registerToolbarButton(Button button, NeuronType type) {
        ensureNotDisposed();
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(type, "type");

        Button registeredButton = toolbarButtons.get(type);
        if (registeredButton == button) {
            return;
        }
        if (registeredButton != null) {
            throw new IllegalArgumentException("A toolbar button is already registered for neuron type " + type);
        }
        for (Map.Entry<NeuronType, Button> entry : toolbarButtons.entrySet()) {
            if (entry.getValue() == button) {
                throw new IllegalArgumentException("The toolbar button is already registered for " + entry.getKey());
            }
        }

        toolbarButtons.put(type, button);
        configureDragSource(button, type);
        updateToolbarButtonSelection();
    }

    /**
     * Установлює активний тип нейрона для постійного розміщення; {@code null} вимикає цей режим.
     *
     * @param type тип нейрона для наступних кліків або {@code null} для завершення режиму.
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     * @throws IllegalArgumentException якщо вказаний тип не має зареєстрованої кнопки інструмента.
     */
    public void setPlacementMode(NeuronType type) {
        ensureNotDisposed();
        if (type != null && !toolbarButtons.containsKey(type)) {
            throw new IllegalArgumentException("No toolbar button is registered for neuron type " + type);
        }
        if (activePlacementType == type) {
            if (type == null) {
                clearPreview();
                hasLastPointerPosition = false;
            }
            updateToolbarButtonSelection();
            return;
        }
        activePlacementType = type;
        hasLastPointerPosition = false;
        clearPreview();
        updateToolbarButtonSelection();
    }

    /** Прибирає попередній перегляд нейрона з полотна, не змінюючи активний тип інструмента. */
    public void clearPreview() {
        if (dragPreview != null) {
            workspace.overlayLayer().getChildren().remove(dragPreview);
        }
        dragPreview = null;
        dragPreviewType = null;
        dragPreviewScale = null;
    }

    /** Відновлює попередній перегляд активного інструмента в останній відомій позиції курсора. */
    public void refreshPlacementPreview() {
        ensureNotDisposed();
        restorePlacementPreview();
    }

    /** Від'єднує обробники полотна й кнопок, вимикає режим розміщення та відновлює курсори кнопок. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        activePlacementType = null;
        hasLastPointerPosition = false;
        updateToolbarButtonSelection();
        if (installed) {
            workspace.node().removeEventHandler(DragEvent.DRAG_OVER, dragOverHandler);
            workspace.node().removeEventHandler(DragEvent.DRAG_EXITED, dragExitedHandler);
            workspace.node().removeEventHandler(DragEvent.DRAG_DROPPED, droppedHandler);
            workspace.node().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            workspace.node().removeEventFilter(MouseEvent.MOUSE_EXITED_TARGET, mouseExitedHandler);
            installed = false;
        }
        dragSources.forEach((button, registration) -> {
            button.removeEventHandler(MouseEvent.DRAG_DETECTED, registration.dragDetectedHandler());
            button.removeEventHandler(MouseEvent.MOUSE_RELEASED, registration.mouseReleasedHandler());
            button.removeEventHandler(DragEvent.DRAG_DONE, registration.dragDoneHandler());
            Cursor currentCursor = button.getCursor();
            if (currentCursor == Cursor.HAND || currentCursor == Cursor.CLOSED_HAND) {
                button.setCursor(registration.originalCursor());
            }
        });
        dragSources.clear();
        toolbarButtons.clear();
        clearPreview();
    }

    /** Налаштовує кнопку для перетягування типу нейрона, не перезаписуючи чужі обробники. */
    private void configureDragSource(Button button, NeuronType type) {
        EventHandler<MouseEvent> detectedHandler = event -> {
            if (disposed) {
                return;
            }
            Dragboard dragboard = button.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);
            clearPreview();
            hasLastPointerPosition = false;
            button.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        };
        EventHandler<MouseEvent> releasedHandler = event -> {
            if (!disposed) {
                button.setCursor(Cursor.HAND);
            }
        };
        EventHandler<DragEvent> doneHandler = event -> {
            if (!disposed) {
                restorePlacementPreview();
                button.setCursor(Cursor.HAND);
            } else {
                clearPreview();
            }
            event.consume();
        };
        DragSourceRegistration registration = new DragSourceRegistration(
                detectedHandler, releasedHandler, doneHandler, button.getCursor());
        button.addEventHandler(MouseEvent.DRAG_DETECTED, detectedHandler);
        button.addEventHandler(MouseEvent.MOUSE_RELEASED, releasedHandler);
        button.addEventHandler(DragEvent.DRAG_DONE, doneHandler);
        dragSources.put(button, registration);
    }

    /** Дозволяє відпускання інструмента над полотном і переміщує попередній перегляд під курсор. */
    private void handleDragOver(DragEvent event) {
        if (disposed) {
            return;
        }
        NeuronType type = dragNeuronType(event.getDragboard());
        if (type == null) {
            return;
        }
        rememberPointer(event.getSceneX(), event.getSceneY());
        event.acceptTransferModes(TransferMode.COPY);
        updateDragPreview(type, event.getSceneX(), event.getSceneY());
        event.consume();
    }

    /** Приховує прев'ю після виходу drag-and-drop за межі полотна, не скасовуючи режим розміщення. */
    private void handleDragExited(DragEvent event) {
        if (disposed || event.getTarget() != workspace.node()) {
            return;
        }
        clearPreview();
        hasLastPointerPosition = false;
    }

    /** Створює нейрон у точці полотна, на яку відпустили інструмент перетягування. */
    private void handleDrop(DragEvent event) {
        if (disposed) {
            event.setDropCompleted(false);
            return;
        }
        rememberPointer(event.getSceneX(), event.getSceneY());
        boolean success = false;
        try {
            NeuronType type = dragNeuronType(event.getDragboard());
            if (type != null) {
                Point2D viewportPoint = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
                Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                        viewportPoint, state.zoom(), state.panX(), state.panY());
                dropConsumer.accept(type, worldPoint);
                success = true;
            }
            event.setDropCompleted(success);
        } finally {
            if (activePlacementType == null) {
                clearPreview();
            } else {
                restorePlacementPreview();
            }
        }
        event.consume();
    }

    /** Оновлює попередній перегляд, доки вказівник рухається по полотну в режимі розміщення. */
    private void handleMouseMoved(MouseEvent event) {
        if (disposed) {
            return;
        }
        rememberPointer(event.getSceneX(), event.getSceneY());
        if (activePlacementType != null) {
            updateDragPreview(activePlacementType, event.getSceneX(), event.getSceneY());
        }
    }

    /** Приховує будь-яке прев'ю, коли вказівник залишає полотно, але ігнорує вихід із дочірніх вузлів. */
    private void handleMouseExited(MouseEvent event) {
        if (disposed || event.getTarget() != workspace.node()) {
            return;
        }
        clearPreview();
        hasLastPointerPosition = false;
    }

    /** Зберігає останню позицію вказівника для відновлення попереднього перегляду після перетягування. */
    private void rememberPointer(double sceneX, double sceneY) {
        lastPointerSceneX = sceneX;
        lastPointerSceneY = sceneY;
        hasLastPointerPosition = true;
    }

    /** Повертає попередній перегляд активного інструмента в останню відому позицію курсора. */
    private void restorePlacementPreview() {
        if (activePlacementType != null && hasLastPointerPosition) {
            updateDragPreview(activePlacementType, lastPointerSceneX, lastPointerSceneY);
        } else if (activePlacementType == null) {
            clearPreview();
        }
    }

    /** Підсвічує лише кнопку активного інструмента й прибирає підсвічування з решти зареєстрованих кнопок. */
    private void updateToolbarButtonSelection() {
        toolbarButtons.forEach((type, button) -> {
            if (type == activePlacementType) {
                if (!button.getStyleClass().contains(ACTIVE_TOOL_STYLE_CLASS)) {
                    button.getStyleClass().add(ACTIVE_TOOL_STYLE_CLASS);
                }
            } else {
                button.getStyleClass().remove(ACTIVE_TOOL_STYLE_CLASS);
            }
        });
    }

    /** Розташовує попередній перегляд нейрона під курсором з урахуванням поточного масштабу камери. */
    private void updateDragPreview(NeuronType type, double sceneX, double sceneY) {
        if (dragPreview == null || dragPreviewType != type) {
            clearPreview();
            dragPreview = NeuronDragPreviewFactory.create(type);
            dragPreview.setMouseTransparent(true);
            dragPreviewType = type;
            dragPreviewScale = new Scale(
                    state.zoom(), state.zoom(),
                    NeuronVisualGeometry.WIDTH / 2.0, NeuronVisualGeometry.HEIGHT / 2.0);
            dragPreview.getTransforms().add(dragPreviewScale);
            workspace.overlayLayer().getChildren().add(dragPreview);
        }
        Point2D cursor = workspace.overlayLayer().sceneToLocal(sceneX, sceneY);
        dragPreviewScale.setX(state.zoom());
        dragPreviewScale.setY(state.zoom());
        Point2D previewTopLeft = NeuronVisualGeometry.topLeftFromCenter(cursor);
        dragPreview.relocate(previewTopLeft.getX(), previewTopLeft.getY());
        dragPreview.toFront();
    }

    /** Розпізнає тип нейрона в буфері перетягування, не поширюючи помилки некоректного введення. */
    private static NeuronType dragNeuronType(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) {
            return null;
        }
        try {
            return NeuronType.valueOf(dragboard.getString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Забороняє повторне налаштування контролера після його звільнення. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron tool drag controller is disposed");
        }
    }

    /** Зберігає посилання на підписки конкретної кнопки та її початковий курсор. */
    private record DragSourceRegistration(
            EventHandler<MouseEvent> dragDetectedHandler,
            EventHandler<MouseEvent> mouseReleasedHandler,
            EventHandler<DragEvent> dragDoneHandler,
            Cursor originalCursor
    ) { }
}
