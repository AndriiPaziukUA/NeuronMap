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
 * Видаляє вибрані або окремі нейрони та оновлює пов’язані візуальні елементи й стан редактора.
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
     * Створює екземпляр NeuronDeletionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param rotationHandles мапа ручок обертання нейронів за ідентифікаторами.
     * @param hideMenu callback для приховування контекстного меню.
     * @param refreshVisuals callback, який оновлює вигляд нейронів.
     * @param refreshPresentation callback, який синхронізує візуальні подання з моделлю.
     * @param refreshDeleteHighlights callback, який оновлює підсвічування зв’язків, доступних для видалення.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
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
     * Створює екземпляр NeuronDeletionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuronService служба операцій над нейронами.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param neuronViews мапа візуальних подань нейронів за їхніми ідентифікаторами.
     * @param rotationHandles мапа ручок обертання нейронів за ідентифікаторами.
     * @param hideMenu callback для приховування контекстного меню.
     * @param refreshVisuals callback, який оновлює вигляд нейронів.
     * @param refreshPresentation callback, який синхронізує візуальні подання з моделлю.
     * @param refreshDeleteHighlights callback, який оновлює підсвічування зв’язків, доступних для видалення.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
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
     * Реєструє обробники подій, потрібні для scene key handler.
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
     * Обробляє подію «key pressed» і передає її до відповідної операції редактора.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
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
     * Видаляє всі вибрані нейрони й повертає ознаку того, чи змінилася модель.
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
     * Видаляє нейрон за ідентифікатором і очищає пов’язані елементи інтерфейсу.
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
     * Видаляє набір нейронів та оновлює залежні візуальні елементи.
     *
     * @param neuronIds ідентифікатори нейронів, які потрібно обробити.
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
     * Перевіряє, чи має фокус текстовий елемент, щоб не перехоплювати введення користувача.
     *
     * @param target цільовий вузол або об’єкт інтерфейсу, який потрібно перевірити чи знайти.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
