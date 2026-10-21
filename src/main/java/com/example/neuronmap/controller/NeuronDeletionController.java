package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.scene.Cursor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Обробляє запити на видалення нейронів і оновлює редакторський стан після успішної операції.
 * Клавіатурні команди належать окремому маршрутизатору гарячих клавіш редактора.
 */
public final class NeuronDeletionController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Runnable refreshPresentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює обробник видалення, який не реєструє глобальних клавіатурних подій і не керує мапами подань.
     *
     * @param neuronService служба видалення нейронів із моделі.
     * @param state стан вибору та режимів редактора.
     * @param workspace робоча область, курсор якої потрібно відновити після видалення.
     * @param hideMenu дія приховування активного контекстного меню.
     * @param refreshPresentation дія синхронізації всіх подань карти з моделлю.
     * @param save дія збереження зміненої карти.
     * @param status дія відображення повідомлення користувачу.
     * @param localization служба локалізації повідомлень.
     */
    public NeuronDeletionController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.hideMenu = Objects.requireNonNull(hideMenu, "hideMenu");
        this.refreshPresentation = Objects.requireNonNull(refreshPresentation, "refreshPresentation");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Видаляє вибрані нейрони або нейрон, для якого відкрите контекстне меню.
     *
     * @return {@code true}, якщо видалено принаймні один нейрон.
     */
    public boolean deleteSelectedNeurons() {
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>(state.selectedNeuronIds());

        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }

        selectedIds.removeIf(id -> neuronService.find(id).isEmpty());
        if (selectedIds.isEmpty()) {
            return false;
        }

        return deleteNeurons(selectedIds);
    }

    /**
     * Видаляє один нейрон за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона, який потрібно видалити.
     * @return {@code true}, якщо нейрон існував і був видалений; інакше {@code false}.
     */
    public boolean deleteNeuron(String neuronId) {
        if (neuronId == null || neuronId.isBlank() || neuronService.find(neuronId).isEmpty()) {
            return false;
        }
        return deleteNeurons(Set.of(neuronId));
    }

    /**
     * Видаляє наявні нейрони через службу й один раз синхронізує подання після завершення операції.
     * Під час активного додавання зберігає вибраний інструмент, щоб видалення вибору не вимикало розміщення.
     *
     * @param neuronIds ідентифікатори нейронів, які потрібно видалити.
     * @return {@code true}, якщо видалено принаймні один нейрон; інакше {@code false}.
     */
    private boolean deleteNeurons(Collection<String> neuronIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(neuronIds);
        boolean keepNeuronPlacement = state.mode() == EditorState.Mode.ADD_NEURON;
        boolean changed = false;
        hideMenu.run();
        for (String neuronId : ids) {
            changed |= neuronService.remove(neuronId).isPresent();
        }

        if (!changed) {
            return false;
        }

        state.clearSelection();
        if (!keepNeuronPlacement) {
            state.resetToIdle();
            workspace.node().setCursor(Cursor.DEFAULT);
        }
        refreshPresentation.run();
        save.run();
        status.accept(
                ids.size() == 1
                        ? localization.text("status.delete_neuron")
                        : localization.text("status.delete_neurons")
        );
        return true;
    }
}
