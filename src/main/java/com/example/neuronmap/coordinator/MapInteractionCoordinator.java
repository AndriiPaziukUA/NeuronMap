package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronToolDragController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.util.WorldCoordinateMapper;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Координує режими взаємодії з полотном і передає зміни моделі координатору презентації. */
public final class MapInteractionCoordinator {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final SelectionController selectionController;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final SimulationController simulationController;
    private final MapPresentationCoordinator presentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;
    private final NeuronToolDragController toolDragController;
    private final EventHandler<MouseEvent> canvasClickHandler = this::handleCanvasClick;

    private boolean installed;
    private boolean disposed;

    /**
     * Створює координатор взаємодії редактора з переданими службами та контролерами.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state стан взаємодії редактора.
     * @param workspace полотно редактора.
     * @param selectionController контролер вибору нейронів.
     * @param connectionController контролер створення й видалення зв'язків.
     * @param neuronController контролер подій нейронів.
     * @param simulationController контролер симуляції.
     * @param presentation координатор візуальних подань.
     * @param save дія збереження поточного стану.
     * @param status дія показу повідомлень стану.
     * @param localization служба локалізації інтерфейсу.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     */
    public MapInteractionCoordinator(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            SelectionController selectionController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            SimulationController simulationController,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.toolDragController = new NeuronToolDragController(state, workspace, this::handleToolDrop);
    }

    /**
     * Реєструє обробники подій полотна й кнопок інструментів. Повторна установка безпечна.
     *
     * @throws IllegalStateException якщо координатор уже звільнив свої ресурси.
     */
    public void install() {
        ensureNotDisposed();
        if (installed) {
            return;
        }
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, canvasClickHandler);
        toolDragController.install();
        installed = true;
    }

    /**
     * Вмикає режим розміщення нейрона або перемикає його на інший тип.
     * Повторне натискання на вже активний тип скасовує режим.
     *
     * @param type тип нейрона, який слід розміщувати.
     * @throws NullPointerException якщо тип нейрона не задано.
     * @throws IllegalStateException якщо координатор уже звільнив свої ресурси.
     */
    public void beginAddNeuronMode(NeuronType type) {
        ensureNotDisposed();
        Objects.requireNonNull(type, "type");
        if (state.mode() == EditorState.Mode.ADD_NEURON && state.pendingNeuronType() == type) {
            cancelInteractions();
            return;
        }
        simulationController.stop();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.enterAddNeuronMode(type);
        toolDragController.setPlacementMode(type);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(localization.text("status.add_neuron_hint"));
    }

    /** Скасовує активні режими взаємодії, попередній перегляд та підсвічування інструмента. */
    public void cancelInteractions() {
        if (disposed) {
            return;
        }
        toolDragController.setPlacementMode(null);
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);
        status.accept(localization.text("status.ready"));
    }

    /**
     * Узгоджує виділення інструмента та його попередній перегляд зі станом редактора після undo/redo.
     * Активний режим розміщення залишається ввімкненим, доки його не скасує користувач.
     */
    void synchronizeNeuronToolPlacement() {
        if (disposed) {
            return;
        }
        NeuronType activeType = state.mode() == EditorState.Mode.ADD_NEURON
                ? state.pendingNeuronType() : null;
        toolDragController.setPlacementMode(activeType);
        toolDragController.refreshPlacementPreview();
        workspace.node().setCursor(activeType == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
    }

    /** Створює нейрон у заданих координатах і синхронізує карту одним викликом координатора презентації. */
    public void addNeuronAt(NeuronType type, double x, double y) {
        ensureNotDisposed();
        if (type == null) {
            return;
        }
        neuronService.create(type, x, y);
        presentation.refreshAll();
        save.run();
        status.accept(localization.text("status.new_neuron"));
    }

    /** Створює групу з вибраних нейронів через відповідну службу вибору. */
    public void groupSelection() {
        ensureNotDisposed();
        selectionController.groupSelection();
        presentation.refreshAll();
    }

    /** Знімає групування з вибраних нейронів. */
    public void ungroupSelection() {
        ensureNotDisposed();
        selectionController.ungroupSelection();
        presentation.refreshAll();
    }

    /** Знімає обробники полотна, завершує тимчасові режими й звільняє ресурси кнопок. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (installed) {
            workspace.node().removeEventFilter(MouseEvent.MOUSE_CLICKED, canvasClickHandler);
            installed = false;
        }
        toolDragController.dispose();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /** Обробляє клацання по полотну відповідно до активного режиму редактора. */
    private void handleCanvasClick(MouseEvent event) {
        if (disposed || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (state.mode() == EditorState.Mode.ADD_NEURON) {
            Point2D viewportPoint = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
            Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                    viewportPoint, state.zoom(), state.panX(), state.panY());
            Point2D neuronTopLeft = NeuronVisualGeometry.topLeftFromCenter(worldPoint);
            addNeuronAt(state.pendingNeuronType(), neuronTopLeft.getX(), neuronTopLeft.getY());
            event.consume();
            return;
        }
        if (neuronController.isInteractiveTarget(event.getTarget())
                || JavaFxNodeLookup.findAncestor(event.getTarget(), ConnectionView.class) != null) {
            return;
        }
        if (state.isIdle()) {
            neuronController.hideMenu();
            selectionController.clear();
            event.consume();
        }
    }

    /** Створює нейрон у точці, куди користувач відпустив інструмент перетягування. */
    private void handleToolDrop(NeuronType type, Point2D worldPoint) {
        if (disposed) {
            return;
        }
        Point2D neuronTopLeft = NeuronVisualGeometry.topLeftFromCenter(worldPoint);
        addNeuronAt(type, neuronTopLeft.getX(), neuronTopLeft.getY());
    }

    /**
     * Під'єднує поточні кнопки інструментів; сумісний метод для двох наявних типів нейронів.
     *
     * @param excitatoryButton кнопка інструмента збуджувального нейрона.
     * @param inhibitoryButton кнопка інструмента гальмівного нейрона.
     * @throws IllegalStateException якщо координатор уже звільнив свої ресурси.
     */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        ensureNotDisposed();
        toolDragController.configureToolbarButtons(excitatoryButton, inhibitoryButton);
    }

    /**
     * Під'єднує довільну кількість кнопок інструментів за картою типів і кнопок.
     *
     * @param buttons відповідність типів нейронів кнопкам, що мають запускати ці інструменти.
     * @throws NullPointerException якщо карту, тип або кнопку не задано.
     * @throws IllegalArgumentException якщо тип або кнопку вже зареєстровано для іншого інструмента.
     * @throws IllegalStateException якщо координатор уже звільнив свої ресурси.
     */
    public void configureToolbarButtons(Map<NeuronType, Button> buttons) {
        ensureNotDisposed();
        toolDragController.configureToolbarButtons(buttons);
    }

    /** Забороняє повторно активувати координатор після звільнення його ресурсів. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Map interaction coordinator is disposed");
        }
    }
}
