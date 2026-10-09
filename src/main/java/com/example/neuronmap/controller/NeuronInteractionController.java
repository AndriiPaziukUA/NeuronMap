package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Обробляє клацання та інші безпосередні дії користувача над нейронами.
 */
public final class NeuronInteractionController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Map<String, RotationHandleView> rotationHandles;
    private final SelectionController selectionController;
    private final ConnectionController connections;
    private final SimulationController simulation;
    private final Runnable save;
    private final Runnable refreshConnections;
    private final NeuronMenuCustomizer menuCustomizer;
    private final NeuronClipboardController clipboardController;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuController menuController;
    private final NeuronRotationController rotationController;
    private final NeuronDragController dragController;

    /**
     * Повертає результат операції «нейрон».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param groupService значення, що визначає група служба для цієї операції.
     *
     * @param selectionController значення, що визначає вибір для цієї операції.
     *
     * @param clipboardService значення, що визначає буфер обміну служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param connections значення, що визначає звʼязки для цієї операції.
     *
     * @param simulation значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param refreshConnections значення, що визначає звʼязки для цієї операції.
     *
     * @param status стан операції.
     *
     * @param menuCustomizer значення, що визначає меню для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronInteractionController(
            NeuronService neuronService,
            GroupService groupService,
            SelectionController selectionController,
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            ConnectionController connections,
            SimulationController simulation,
            Runnable save,
            Runnable refreshConnections,
            Consumer<String> status,
            NeuronMenuCustomizer menuCustomizer
    ) {
        this(
                neuronService,
                groupService,
                selectionController,
                clipboardService,
                state,
                workspace,
                neuronViews,
                rotationHandles,
                connections,
                simulation,
                save,
                refreshConnections,
                status,
                menuCustomizer,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «нейрон».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param groupService значення, що визначає група служба для цієї операції.
     *
     * @param selectionController значення, що визначає вибір для цієї операції.
     *
     * @param clipboardService значення, що визначає буфер обміну служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param neuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param rotationHandles значення, що визначає обертання обробляє для цієї операції.
     *
     * @param connections значення, що визначає звʼязки для цієї операції.
     *
     * @param simulation значення, що визначає відповідну операцію для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param refreshConnections значення, що визначає звʼязки для цієї операції.
     *
     * @param status стан операції.
     *
     * @param menuCustomizer значення, що визначає меню для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronInteractionController(
            NeuronService neuronService,
            GroupService groupService,
            SelectionController selectionController,
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            ConnectionController connections,
            SimulationController simulation,
            Runnable save,
            Runnable refreshConnections,
            Consumer<String> status,
            NeuronMenuCustomizer menuCustomizer,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        Objects.requireNonNull(groupService, "groupService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.rotationHandles = Objects.requireNonNull(rotationHandles, "rotationHandles");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.save = Objects.requireNonNull(save, "save");
        this.refreshConnections = Objects.requireNonNull(refreshConnections, "refreshConnections");
        this.menuCustomizer = Objects.requireNonNull(menuCustomizer, "menuCustomizer");

        LocalizationService sharedLocalization = Objects.requireNonNull(
                localization,
                "localization"
        );

        this.clipboardController = new NeuronClipboardController(
                clipboardService,
                state,
                workspace,
                this::hideMenu,
                this::addViewForNeuron,
                refreshConnections,
                save,
                status,
                sharedLocalization
        );

        this.deletionController = new NeuronDeletionController(
                neuronService,
                state,
                workspace,
                neuronViews,
                rotationHandles,
                this::hideMenu,
                this::refreshVisuals,
                refreshConnections,
                connections::refreshDeleteHighlights,
                save,
                status,
                sharedLocalization
        );

        this.menuController = new NeuronMenuController(
                neuronService,
                state,
                workspace,
                connections,
                simulation,
                deletionController,
                menuCustomizer,
                this::refreshVisuals,
                refreshConnections,
                this::refreshOverlayPositions,
                save,
                status,
                sharedLocalization
        );

        this.rotationController = new NeuronRotationController(
                neuronService,
                state,
                workspace,
                neuronViews,
                rotationHandles,
                this::hideMenu,
                this::refreshVisuals,
                refreshConnections,
                this::refreshOverlayPositions,
                save
        );

        this.dragController = new NeuronDragController(
                neuronService,
                groupService,
                selectionController,
                state,
                workspace,
                this::hideMenu,
                rotationController::show,
                this::refreshVisuals,
                refreshConnections,
                this::refreshOverlayPositions,
                save
        );
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     */
    public void loadViews() {
        for (Neuron neuron : neuronService.neurons()) {
            createView(neuron);
        }
        refreshVisuals();
        refreshOverlayPositions();
    }

    /**
     * Виконує операцію «додати відображення для нейрон».
     *
     * @param neuron нейрон, над яким виконується операція.
     */
    public void addViewForNeuron(Neuron neuron) {
        if (neuronViews.containsKey(neuron.id())) {
            return;
        }
        createView(neuron);
        refreshVisuals();
        refreshOverlayPositions();
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Collection<NeuronView> views() {
        return neuronViews.values();
    }

    /**
     * Перевіряє, чи виконується умова «кінцевий».
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isInteractiveTarget(Object target) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null) {
            if (node instanceof NeuronView
                    || node instanceof RotationHandleView
                    || node == menuController.node()
                    || node instanceof javafx.scene.control.Control) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    /**
     * Обробляє «відповідну операцію».
     */
    public void refreshVisuals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.refreshVisuals(
                    state.selectedNeuronIds().contains(neuronView.model().id())
            );
        }
    }

    /**
     * Обробляє «накладка».
     */
    public void refreshOverlayPositions() {
        for (NeuronView neuronView : neuronViews.values()) {
            if (menuController.isVisible()
                    && state.selectedNeuronForMenu() != null
                    && state.selectedNeuronForMenu().equals(neuronView.model().id())) {
                menuController.refreshPosition(neuronView);
            }
        }
        rotationController.refreshPosition();
    }

    /**
     * Відображає «меню» в інтерфейсі.
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     */
    public void showMenu(NeuronView neuronView) {
        rotationController.hideAll();
        menuController.show(neuronView);
    }

    /**
     * Виконує операцію «приховати меню».
     */
    public void hideMenu() {
        menuController.hide();
        rotationController.hideAll();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «вибраний нейрони».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean deleteSelectedNeurons() {
        return deletionController.deleteSelectedNeurons();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void dispose() {
        clipboardController.clear();
        hideMenu();
    }

    /**
     * Створює обʼєкт із переданих даних «відображення».
     *
     * @param neuron нейрон, над яким виконується операція.
     */
    private void createView(Neuron neuron) {
        var presentation = neuronService.presentation(neuron.id());
        if (presentation == null) {
            throw new IllegalStateException("Missing presentation for neuron " + neuron.id());
        }

        NeuronView neuronView = new NeuronView(presentation);
        neuronViews.put(neuron.id(), neuronView);
        workspace.nodeLayer().getChildren().add(neuronView);
        rotationController.createHandle(neuronView);

        neuronView.addEventHandler(
                MouseEvent.MOUSE_PRESSED,
                event -> dragController.handlePressed(neuronView, event)
        );
        neuronView.addEventHandler(
                MouseEvent.MOUSE_DRAGGED,
                event -> dragController.handleDragged(neuronView, event)
        );
        neuronView.addEventHandler(
                MouseEvent.MOUSE_RELEASED,
                event -> dragController.handleReleased(neuronView, event)
        );
        neuronView.addEventHandler(
                MouseEvent.MOUSE_CLICKED,
                event -> handleNeuronClicked(neuronView, event)
        );
    }

    /**
     * Обробляє «нейрон».
     *
     * @param neuronView значення, що визначає нейрон відображення для цієї операції.
     *
     * @param event подія інтерфейсу.
     */
    private void handleNeuronClicked(NeuronView neuronView, MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            if (!state.isIdle()) {
                return;
            }
            selectionController.selectOnly(neuronView.model().id());
            showMenu(neuronView);
            event.consume();
            return;
        }

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (event.getClickCount() == 2
                && !neuronView.wasDragged()
                && state.isIdle()) {
            simulation.emitPulse(neuronView.model().id());
            event.consume();
            return;
        }

        if (!state.isIdle() || neuronView.wasDragged()) {
            return;
        }

        rotationController.show(neuronView.model().id());
        event.consume();
    }
}
