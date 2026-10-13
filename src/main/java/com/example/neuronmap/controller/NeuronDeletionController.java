package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.JavaFxNodeLookup;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Обробляє запити на видалення нейронів і оновлює редакторський стан після успішної операції.
 * Синхронізацію графічних подань делегує координатору презентації через callback.
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
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;

    /**
     * Створює обробник видалення, який не керує мапами подань і вузлами JavaFX напряму.
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
        this.neuronService = java.util.Objects.requireNonNull(neuronService, "neuronService");
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.hideMenu = java.util.Objects.requireNonNull(hideMenu, "hideMenu");
        this.refreshPresentation = java.util.Objects.requireNonNull(
                refreshPresentation,
                "refreshPresentation"
        );
        this.save = java.util.Objects.requireNonNull(save, "save");
        this.status = java.util.Objects.requireNonNull(status, "status");
        this.localization = java.util.Objects.requireNonNull(localization, "localization");
        installSceneKeyHandler();
    }

    /**
     * Реєструє обробник клавіші Delete для сцени, до якої приєднана робоча область.
     */
    private void installSceneKeyHandler() {
        workspace.node().sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
                    if (oldScene != null) {
                        oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                    }
                    if (newScene != null) {
                        newScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                    }
                }
        );
    }

    /**
     * Передає команду Delete операції видалення, якщо фокус не перебуває в текстовому полі.
     *
     * @param event подія натискання клавіші.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.DELETE
                || isTextInputTarget(event.getTarget())) {
            return;
        }

        if (deleteSelectedNeurons()) {
            event.consume();
        }
    }

    /**
     * Видаляє вибрані нейрони або нейрон, для якого відкрите контекстне меню.
     *
     * @return {@code true}, якщо видалено принаймні один нейрон.
     */
    public boolean deleteSelectedNeurons() {
        LinkedHashSet<String> selectedIds =
                new LinkedHashSet<>(state.selectedNeuronIds());

        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }

        selectedIds.removeIf(id -> neuronService.find(id) == null);
        if (selectedIds.isEmpty()) {
            return false;
        }

        deleteNeurons(selectedIds);
        return true;
    }

    /**
     * Видаляє один нейрон за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона, який потрібно видалити.
     */
    public void deleteNeuron(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return;
        }
        deleteNeurons(Set.of(neuronId));
    }

    /**
     * Видаляє наявні нейрони через службу й один раз синхронізує подання після завершення операції.
     *
     * @param neuronIds ідентифікатори нейронів, які потрібно видалити.
     */
    private void deleteNeurons(Collection<String> neuronIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(neuronIds);
        boolean changed = false;

        hideMenu.run();
        for (String neuronId : ids) {
            changed |= neuronService.remove(neuronId) != null;
        }

        if (!changed) {
            return;
        }

        state.clearSelection();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);

        refreshPresentation.run();
        save.run();
        status.accept(
                ids.size() == 1
                        ? localization.text("status.delete_neuron")
                        : localization.text("status.delete_neurons")
        );
    }

    /**
     * Перевіряє, чи має фокус текстовий елемент, щоб не перехоплювати введення користувача.
     *
     * @param target цільовий вузол JavaFX.
     * @return {@code true}, якщо фокус перебуває в текстовому полі.
     */
    private boolean isTextInputTarget(Object target) {
        return JavaFxNodeLookup.findAncestor(target, TextInputControl.class) != null;
    }
}
