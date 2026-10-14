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
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.JavaFxNodeLookup;
import com.example.neuronmap.view.NeuronVisualGeometry;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Координує обробку подій полотна та передає зміни моделі до координатора презентації.
 */
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
        this.toolDragController = new NeuronToolDragController(
                state,
                workspace,
                this::handleToolDrop
        );
    }

    /**
     * Реєструє обробники подій полотна й кнопок інструментів.
     */
    public void install() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCanvasClick);
        toolDragController.install();
    }

    /**
     * Починає режим додавання нейрона та готує стан взаємодії.
     *
     * @param type тип нейрона.
     */
    public void beginAddNeuronMode(NeuronType type) {
        simulationController.stop();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();

        state.enterAddNeuronMode(type);
        workspace.node().setCursor(Cursor.CROSSHAIR);
        status.accept(localization.text("status.add_neuron_hint"));
    }

    /**
     * Скасовує незавершені режими взаємодії та очищує тимчасові подання.
     */
    public void cancelInteractions() {
        toolDragController.clearPreview();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);
        status.accept(localization.text("status.ready"));
    }

    /**
     * Створює нейрон у заданих координатах і синхронізує карту одним викликом координатора презентації.
     *
     * @param type тип нейрона.
     * @param x світова координата X.
     * @param y світова координата Y.
     */
    public void addNeuronAt(NeuronType type, double x, double y) {
        if (type == null) {
            return;
        }

        neuronService.create(type, x, y);
        presentation.refreshAll();
        save.run();
        status.accept(localization.text("status.new_neuron"));
    }

    /**
     * Створює групу з вибраних нейронів через відповідну службу вибору.
     */
    public void groupSelection() {
        selectionController.groupSelection();
        presentation.refreshAll();
    }

    /**
     * Знімає групування з вибраних нейронів.
     */
    public void ungroupSelection() {
        selectionController.ungroupSelection();
        presentation.refreshAll();
    }

    /**
     * Завершує режим видалення зв'язків і прибирає його тимчасове підсвічування.
     */
    public void exitDeleteConnectionMode() {
        connectionController.exitDelete();
        status.accept(localization.text("status.delete_mode_exit"));
    }

    /**
     * Очищає тимчасовий попередній перегляд перетягування.
     */
    public void dispose() {
        toolDragController.clearPreview();
    }

    /**
     * Обробляє клацання по полотну відповідно до активного режиму редактора.
     *
     * @param event подія миші.
     */
    private void handleCanvasClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (neuronController.isInteractiveTarget(event.getTarget())
                || JavaFxNodeLookup.findAncestor(event.getTarget(), ConnectionView.class) != null) {
            return;
        }

        if (state.mode() == EditorState.Mode.ADD_NEURON) {
            Point2D viewportPoint = workspace.node().sceneToLocal(
                    event.getSceneX(),
                    event.getSceneY()
            );
            Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                    viewportPoint,
                    state.zoom(),
                    state.panX(),
                    state.panY()
            );
            Point2D neuronTopLeft = NeuronVisualGeometry.topLeftFromCenter(worldPoint);
            addNeuronAt(
                    state.pendingNeuronType(),
                    neuronTopLeft.getX(),
                    neuronTopLeft.getY()
            );
            state.resetToIdle();
            workspace.node().setCursor(Cursor.DEFAULT);
            event.consume();
            return;
        }

        if (state.isIdle()) {
            neuronController.hideMenu();
            selectionController.clear();
            event.consume();
        }
    }

    /**
     * Створює нейрон у точці, куди користувач відпустив інструмент перетягування.
     *
     * @param type тип нейрона.
     * @param worldPoint точка центра у світових координатах карти.
     */
    private void handleToolDrop(NeuronType type, Point2D worldPoint) {
        Point2D neuronTopLeft = NeuronVisualGeometry.topLeftFromCenter(worldPoint);
        addNeuronAt(type, neuronTopLeft.getX(), neuronTopLeft.getY());
    }

    /**
     * Під'єднує перетягування до кнопок додавання збуджувального й гальмівного нейронів.
     *
     * @param excitatoryButton кнопка додавання збуджувального нейрона.
     * @param inhibitoryButton кнопка додавання гальмівного нейрона.
     */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        toolDragController.configureToolbarButtons(excitatoryButton, inhibitoryButton);
    }
}
