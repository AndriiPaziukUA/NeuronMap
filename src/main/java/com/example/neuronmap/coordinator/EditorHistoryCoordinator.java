package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Пов’язує операції скасування й повторення з оновленням візуального подання та стану взаємодії редактора.
 */
public final class EditorHistoryCoordinator {

    private final HistoryService historyService;
    private final SimulationController simulationController;
    private final ConnectionController connectionController;
    private final NeuronInteractionController neuronController;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable removeDragPreview;
    private final MapPresentationCoordinator presentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює екземпляр EditorHistoryCoordinator та зберігає передані залежності, потрібні для його роботи.
     *
     * @param historyService служба скасування й повторення змін.
     * @param simulationController значення «simulation controller», яке використовується в цьому методі.
     * @param connectionController значення «connection controller», яке використовується в цьому методі.
     * @param neuronController значення «neuron controller», яке використовується в цьому методі.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param removeDragPreview callback, який прибирає попередній перегляд перетягування.
     * @param presentation візуальне подання нейрона.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     */
    public EditorHistoryCoordinator(
            HistoryService historyService,
            SimulationController simulationController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            EditorState state,
            WorkspaceView workspace,
            Runnable removeDragPreview,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                historyService,
                simulationController,
                connectionController,
                neuronController,
                state,
                workspace,
                removeDragPreview,
                presentation,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює екземпляр EditorHistoryCoordinator та зберігає передані залежності, потрібні для його роботи.
     *
     * @param historyService служба скасування й повторення змін.
     * @param simulationController значення «simulation controller», яке використовується в цьому методі.
     * @param connectionController значення «connection controller», яке використовується в цьому методі.
     * @param neuronController значення «neuron controller», яке використовується в цьому методі.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param removeDragPreview callback, який прибирає попередній перегляд перетягування.
     * @param presentation візуальне подання нейрона.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
     */
    public EditorHistoryCoordinator(
            HistoryService historyService,
            SimulationController simulationController,
            ConnectionController connectionController,
            NeuronInteractionController neuronController,
            EditorState state,
            WorkspaceView workspace,
            Runnable removeDragPreview,
            MapPresentationCoordinator presentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.simulationController = Objects.requireNonNull(simulationController, "simulationController");
        this.connectionController = Objects.requireNonNull(connectionController, "connectionController");
        this.neuronController = Objects.requireNonNull(neuronController, "neuronController");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.removeDragPreview = Objects.requireNonNull(removeDragPreview, "removeDragPreview");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Скасовує останню зміну карти й оновлює залежні частини інтерфейсу.
     */
    public boolean undo() {
        simulationController.stop();
        if (!historyService.undo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        save.run();
        status.accept(localization.text("status.undo"));
        return true;
    }

    /**
     * Повторює скасовану зміну карти й оновлює залежні частини інтерфейсу.
     */
    public boolean redo() {
        simulationController.stop();
        if (!historyService.redo()) {
            return false;
        }
        resetEditorAfterHistoryChange();
        presentation.synchronizeViewsWithModel();
        save.run();
        status.accept(localization.text("status.redo"));
        return true;
    }

    /**
     * Скидає тимчасові стани взаємодії після відновлення іншого знімка карти.
     */
    private void resetEditorAfterHistoryChange() {
        removeDragPreview.run();
        connectionController.cancelCreate();
        connectionController.exitDelete();
        neuronController.hideMenu();
        state.resetToIdle();
        state.clearSelection();
        workspace.node().setCursor(Cursor.DEFAULT);
    }
}
