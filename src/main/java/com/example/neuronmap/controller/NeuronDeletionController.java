package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Організовує видалення вибраних нейронів і повʼязаних із ними елементів.
 */
public final class NeuronDeletionController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final Runnable hideMenu;
    private final Runnable refreshVisuals;
    private final Runnable refreshPresentation;
    private final Runnable refreshDeleteHighlights;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;

    /**
     * Повертає результат операції «нейрон».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param hideMenu значення, що визначає приховати меню для цієї операції.
     *
     * @param refreshVisuals значення, що визначає відповідну операцію для цієї операції.
     *
     * @param refreshPresentation значення, що визначає представлення для цієї операції.
     *
     * @param refreshDeleteHighlights значення, що визначає видалити для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronDeletionController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            Runnable hideMenu,
            Runnable refreshVisuals,
            Runnable refreshPresentation,
            Runnable refreshDeleteHighlights,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                neuronService,
                state,
                workspace,
                neuronViews,
                rotationHandles,
                hideMenu,
                refreshVisuals,
                refreshPresentation,
                refreshDeleteHighlights,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «нейрон».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param hideMenu значення, що визначає приховати меню для цієї операції.
     *
     * @param refreshVisuals значення, що визначає відповідну операцію для цієї операції.
     *
     * @param refreshPresentation значення, що визначає представлення для цієї операції.
     *
     * @param refreshDeleteHighlights значення, що визначає видалити для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronDeletionController(
            NeuronService neuronService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            Runnable hideMenu,
            Runnable refreshVisuals,
            Runnable refreshPresentation,
            Runnable refreshDeleteHighlights,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.neuronService = neuronService;
        this.state = state;
        this.workspace = workspace;
        this.neuronViews = neuronViews;
        this.rotationHandles = rotationHandles;
        this.hideMenu = hideMenu;
        this.refreshVisuals = refreshVisuals;
        this.refreshPresentation = refreshPresentation;
        this.refreshDeleteHighlights = refreshDeleteHighlights;
        this.save = save;
        this.status = status;
        this.localization = localization;
        installSceneKeyHandler();
    }

    /**
     * Виконує операцію «сцена ключ».
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
     * Обробляє «ключ».
     *
     * @param event подія інтерфейсу.
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
     * Видаляє або скидає дані, повʼязані з «вибраний нейрони».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Видаляє або скидає дані, повʼязані з «нейрон».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void deleteNeuron(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return;
        }
        deleteNeurons(Set.of(neuronId));
    }

    /**
     * Видаляє або скидає дані, повʼязані з «нейрони».
     *
     * @param neuronIds ідентифікатори нейронів.
     */
    private void deleteNeurons(Collection<String> neuronIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(neuronIds);
        boolean changed = false;

        hideMenu.run();

        for (String neuronId : ids) {
            if (neuronService.remove(neuronId) == null) {
                continue;
            }

            changed = true;
            neuronViews.remove(neuronId);

            RotationHandleView removedHandle = rotationHandles.remove(neuronId);
            if (removedHandle != null) {
                removedHandle.dispose();
                workspace.overlayLayer().getChildren().remove(removedHandle);
            }
        }

        if (!changed) {
            return;
        }

        workspace.nodeLayer().getChildren().removeIf(
                node -> node instanceof NeuronView neuronView
                        && neuronService.find(neuronView.model().id()) == null
        );

        state.clearSelection();
        state.resetToIdle();
        workspace.node().setCursor(Cursor.DEFAULT);

        refreshVisuals.run();
        refreshPresentation.run();
        refreshDeleteHighlights.run();
        save.run();

        status.accept(
                ids.size() == 1
                        ? localization.text("status.delete_neuron")
                        : localization.text("status.delete_neurons")
        );
    }

    /**
     * Перевіряє, чи виконується умова «текст вхід кінцевий».
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    private boolean isTextInputTarget(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node instanceof TextInputControl) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}
