package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.JavaFxNodeLookup;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronToolDragController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Узгоджує дії користувача, що залучають кілька контролерів редактора.
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
     * Повертає результат операції «карта».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param selectionController значення, що визначає вибір для цієї операції.
     *
     * @param connectionController значення, що визначає звʼязок для цієї операції.
     *
     * @param neuronController значення, що визначає нейрон для цієї операції.
     *
     * @param simulationController значення, що визначає відповідну операцію для цієї операції.
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
            Consumer<String> status
    ) {
        this(
                neuronService,
                state,
                workspace,
                selectionController,
                connectionController,
                neuronController,
                simulationController,
                presentation,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «карта».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param selectionController значення, що визначає вибір для цієї операції.
     *
     * @param connectionController значення, що визначає звʼязок для цієї операції.
     *
     * @param neuronController значення, що визначає нейрон для цієї операції.
     *
     * @param simulationController значення, що визначає відповідну операцію для цієї операції.
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «відповідну операцію».
     */
    public void install() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleCanvasClick);
        toolDragController.install();
    }

    /**
     * Запускає або планує дію, повʼязану з «додати нейрон».
     *
     * @param type тип обʼєкта.
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
     * Завершує або скасовує дію, повʼязану з «відповідну операцію».
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
     * Виконує операцію «додати нейрон».
     *
     * @param type тип обʼєкта.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     */
    public void addNeuronAt(NeuronType type, double x, double y) {
        if (type == null) {
            return;
        }

        Neuron neuron = neuronService.create(type, x, y);
        neuronController.addViewForNeuron(neuron);
        presentation.refreshAll();
        save.run();
        status.accept(localization.text("status.new_neuron"));
    }

    /**
     * Виконує операцію «група вибір».
     */
    public void groupSelection() {
        selectionController.groupSelection();
        presentation.refreshAll();
    }

    /**
     * Виконує операцію «вибір».
     */
    public void ungroupSelection() {
        selectionController.ungroupSelection();
        presentation.refreshAll();
    }

    /**
     * Виконує операцію «видалити звʼязок».
     */
    public void exitDeleteConnectionMode() {
        connectionController.exitDelete();
        status.accept(localization.text("status.delete_mode_exit"));
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void dispose() {
        toolDragController.clearPreview();
    }

    /**
     * Обробляє «відповідну операцію».
     *
     * @param event подія інтерфейсу.
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
            Point2D worldPoint = screenToWorld(event.getX(), event.getY());
            addNeuronAt(
                    state.pendingNeuronType(),
                    worldPoint.getX() - NeuronView.WIDTH / 2.0,
                    worldPoint.getY() - NeuronView.HEIGHT / 2.0
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
     * Обробляє «відповідну операцію».
     *
     * @param type тип обʼєкта.
     *
     * @param worldPoint значення, що визначає карта для цієї операції.
     */
    private void handleToolDrop(NeuronType type, Point2D worldPoint) {
        addNeuronAt(
                type,
                worldPoint.getX() - NeuronView.WIDTH / 2.0,
                worldPoint.getY() - NeuronView.HEIGHT / 2.0
        );
    }

    /**
     * Задає або оновлює значення, повʼязані з «панель інструментів».
     *
     * @param excitatoryButton значення, що визначає кнопка для цієї операції.
     *
     * @param inhibitoryButton значення, що визначає кнопка для цієї операції.
     */
    public void configureToolbarButtons(Button excitatoryButton, Button inhibitoryButton) {
        toolDragController.configureToolbarButtons(excitatoryButton, inhibitoryButton);
    }

    /**
     * Повертає результат операції «до карта».
     *
     * @param screenX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param screenY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - state.panX()) / state.zoom(),
                (screenY - state.panY()) / state.zoom()
        );
    }
}
