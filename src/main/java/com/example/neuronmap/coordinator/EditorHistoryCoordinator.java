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
 * Організовує скасування й повторення змін редактора та синхронізацію його стану.
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
     * Повертає результат операції «історія».
     *
     * @param historyService значення, що визначає історія служба для цієї операції.
     *
     * @param simulationController значення, що визначає відповідну операцію для цієї операції.
     *
     * @param connectionController значення, що визначає звʼязок для цієї операції.
     *
     * @param neuronController значення, що визначає нейрон для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param removeDragPreview значення, що визначає видалити перетягування попередній перегляд для цієї операції.
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «історія».
     *
     * @param historyService значення, що визначає історія служба для цієї операції.
     *
     * @param simulationController значення, що визначає відповідну операцію для цієї операції.
     *
     * @param connectionController значення, що визначає звʼязок для цієї операції.
     *
     * @param neuronController значення, що визначає нейрон для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param removeDragPreview значення, що визначає видалити перетягування попередній перегляд для цієї операції.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Видаляє або скидає дані, повʼязані з «після історія змінити».
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
