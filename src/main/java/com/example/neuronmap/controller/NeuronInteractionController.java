package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.JavaFxNodeLookup;
import com.example.neuronmap.view.NeuronMenuView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.RotationHandleView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.scene.control.Control;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Обробляє користувацьку взаємодію з нейронами та координує підлеглі контролери.
 * Створення, вилучення й синхронізацію самих графічних подань делегує координатору презентації.
 */
public final class NeuronInteractionController {

    private final NeuronService neuronService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final SelectionController selectionController;
    private final SimulationController simulation;
    private final NeuronClipboardController clipboardController;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuController menuController;
    private final NeuronRotationController rotationController;
    private final NeuronDragController dragController;
    private Runnable neuronViewSynchronizer;

    /**
     * Створює контролер взаємодії з типовою українською локалізацією.
     *
     * @param neuronService служба операцій над нейронами.
     * @param groupService служба операцій над групами нейронів.
     * @param selectionController контролер вибору нейронів.
     * @param clipboardService служба копіювання та вставлення нейронів.
     * @param state стан редактора.
     * @param workspace полотно редактора.
     * @param neuronViews спільна мапа візуальних подань нейронів.
     * @param rotationHandles спільна мапа ручок обертання.
     * @param connections контролер роботи зі зв'язками.
     * @param simulation контролер симуляції.
     * @param save дія збереження поточного стану.
     * @param refreshConnections дія оновлення всієї презентації карти.
     * @param status дія показу повідомлення стану.
     * @param neuronMenuView подання оформлення кнопок контекстного меню.
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
            NeuronMenuView neuronMenuView
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
                neuronMenuView,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює контролер і підлеглі обробники кліків, перетягування, вибору, буфера обміну та видалення.
     *
     * @param neuronService служба операцій над нейронами.
     * @param groupService служба операцій над групами нейронів.
     * @param selectionController контролер вибору нейронів.
     * @param clipboardService служба копіювання та вставлення нейронів.
     * @param state стан редактора.
     * @param workspace полотно редактора.
     * @param neuronViews спільна мапа візуальних подань нейронів.
     * @param rotationHandles спільна мапа ручок обертання.
     * @param connections контролер роботи зі зв'язками.
     * @param simulation контролер симуляції.
     * @param save дія збереження поточного стану.
     * @param refreshConnections дія оновлення всієї презентації карти.
     * @param status дія показу повідомлення стану.
     * @param neuronMenuView подання оформлення кнопок контекстного меню.
     * @param localization спільна служба локалізації.
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
            NeuronMenuView neuronMenuView,
            LocalizationService localization
    ) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        Objects.requireNonNull(groupService, "groupService");
        this.state = Objects.requireNonNull(state, "state");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.neuronViews = Objects.requireNonNull(neuronViews, "neuronViews");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.simulation = Objects.requireNonNull(simulation, "simulation");

        LocalizationService sharedLocalization = Objects.requireNonNull(
                localization,
                "localization"
        );

        this.clipboardController = new NeuronClipboardController(
                clipboardService,
                state,
                workspace,
                this::hideMenu,
                refreshConnections,
                save,
                status,
                sharedLocalization
        );

        this.deletionController = new NeuronDeletionController(
                neuronService,
                state,
                workspace,
                this::hideMenu,
                refreshConnections,
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
                neuronMenuView,
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
     * Передає координатору презентації запит на синхронізацію нейронних подань.
     * Використовується точкою складання застосунку після створення координатора.
     *
     * @param synchronizer дія синхронізації подань із моделлю.
     */
    public void setNeuronViewSynchronizer(Runnable synchronizer) {
        if (neuronViewSynchronizer != null) {
            throw new IllegalStateException("Neuron view synchronizer is already configured");
        }
        neuronViewSynchronizer = Objects.requireNonNull(synchronizer, "synchronizer");
    }

    /**
     * Забезпечує початкову синхронізацію подань через їхнього єдиного власника.
     * Зберігає сумісність із наявною точкою складання редактора.
     */
    public void loadViews() {
        if (neuronViewSynchronizer == null) {
            throw new IllegalStateException(
                    "Neuron view synchronizer must be configured before loading views"
            );
        }
        neuronViewSynchronizer.run();
    }

    /**
     * Встановлює обробники користувацьких подій на вже створене координатором подання нейрона.
     *
     * @param neuronView подання, для якого потрібно активувати взаємодію.
     * @param rotationHandle ручка обертання, створена координатором презентації.
     */
    public void installInteractionHandlers(
            NeuronView neuronView,
            RotationHandleView rotationHandle
    ) {
        Objects.requireNonNull(neuronView, "neuronView");
        Objects.requireNonNull(rotationHandle, "rotationHandle");
        rotationController.installHandleHandlers(neuronView, rotationHandle);

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
                event -> dragController.handleReleased(neuronView)
        );
        neuronView.addEventHandler(
                MouseEvent.MOUSE_CLICKED,
                event -> handleNeuronClicked(neuronView, event)
        );
    }

    /**
     * Перевіряє, чи є ціль подією інтерактивним елементом, який має сам обробляти взаємодію.
     *
     * @param target цільовий вузол або об'єкт інтерфейсу.
     * @return {@code true}, якщо подію має обробити вкладений інтерактивний елемент.
     */
    public boolean isInteractiveTarget(Object target) {
        return JavaFxNodeLookup.findAncestorMatching(
                target,
                node -> node instanceof NeuronView
                        || node instanceof RotationHandleView
                        || node == menuController.node()
                        || node instanceof Control
        ) != null;
    }

    /**
     * Оновлює вигляд нейронів відповідно до вибору та поточного стану моделі.
     */
    public void refreshVisuals() {
        for (NeuronView neuronView : neuronViews.values()) {
            neuronView.refreshVisuals(
                    state.selectedNeuronIds().contains(neuronView.model().id())
            );
        }
    }

    /**
     * Перераховує положення контекстних меню й ручок обертання біля нейронів.
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
     * Відкриває контекстне меню вказаного нейрона.
     *
     * @param neuronView візуальне подання нейрона.
     */
    public void showMenu(NeuronView neuronView) {
        rotationController.hideAll();
        menuController.show(neuronView);
    }

    /**
     * Приховує контекстне меню нейрона.
     */
    public void hideMenu() {
        menuController.hide();
        rotationController.hideAll();
    }

    /**
     * Передає видалення поточного вибору відповідному обробнику.
     *
     * @return {@code true}, якщо видалено принаймні один нейрон.
     */
    public boolean deleteSelectedNeurons() {
        return deletionController.deleteSelectedNeurons();
    }

    /**
     * Від'єднує обробники й очищає ресурси, створені контролером взаємодії.
     */
    public void dispose() {
        clipboardController.clear();
        hideMenu();
    }

    /**
     * Обробляє клацання по нейрону з урахуванням кнопки миші та активного режиму редактора.
     *
     * @param neuronView подання нейрона, по якому клацнули.
     * @param event подія миші.
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
