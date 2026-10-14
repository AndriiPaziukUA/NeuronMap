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
 * Створення, вилучення й оновлення графічних подань делегує координатору презентації.
 */
public final class NeuronInteractionController {

    private final EditorState state;
    private final SelectionController selectionController;
    private final SimulationController simulation;
    private final NeuronClipboardController clipboardController;
    private final NeuronDeletionController deletionController;
    private final NeuronMenuController menuController;
    private final NeuronRotationController rotationController;
    private final NeuronDragController dragController;
    private Runnable neuronViewSynchronizer;
    private Runnable neuronVisualRefresher;
    private Runnable overlayPositionRefresher;
    private Runnable relatedViewRefresher;

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
     * @param refreshPresentation дія повної синхронізації після зміни складу моделі.
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
            Runnable refreshPresentation,
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
                refreshPresentation,
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
     * @param refreshPresentation дія повної синхронізації після зміни складу моделі.
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
            Runnable refreshPresentation,
            Consumer<String> status,
            NeuronMenuView neuronMenuView,
            LocalizationService localization
    ) {
        Objects.requireNonNull(neuronService, "neuronService");
        Objects.requireNonNull(groupService, "groupService");
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(neuronViews, "neuronViews");
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
                refreshPresentation,
                save,
                status,
                sharedLocalization
        );

        this.deletionController = new NeuronDeletionController(
                neuronService,
                state,
                workspace,
                this::hideMenu,
                refreshPresentation,
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
                this::refreshRelatedViews,
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
                this::refreshRelatedViews,
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
                this::refreshRelatedViews,
                this::refreshOverlayPositions,
                save
        );
    }

    /**
     * Передає координатору презентації синхронізацію подань і оновлення їхнього візуального стану.
     *
     * @param synchronizer дія узгодження набору нейронних подань із моделлю.
     * @param visualRefresher дія оновлення вигляду вже створених подань.
     * @param overlayPositionRefresher дія узгодження позицій контекстного меню та ручок обертання.
     * @param relatedViewRefresher дія оновлення зв'язків і залежної геометрії груп без повторної синхронізації нейронів.
     * @throws IllegalStateException якщо дії презентації вже були зареєстровані.
     */
    public void setPresentationActions(
            Runnable synchronizer,
            Runnable visualRefresher,
            Runnable overlayPositionRefresher,
            Runnable relatedViewRefresher
    ) {
        if (neuronViewSynchronizer != null
                || neuronVisualRefresher != null
                || this.overlayPositionRefresher != null
                || this.relatedViewRefresher != null) {
            throw new IllegalStateException("Presentation actions are already configured");
        }
        neuronViewSynchronizer = Objects.requireNonNull(synchronizer, "synchronizer");
        neuronVisualRefresher = Objects.requireNonNull(visualRefresher, "visualRefresher");
        this.overlayPositionRefresher = Objects.requireNonNull(
                overlayPositionRefresher,
                "overlayPositionRefresher"
        );
        this.relatedViewRefresher = Objects.requireNonNull(
                relatedViewRefresher,
                "relatedViewRefresher"
        );
    }

    /**
     * Забезпечує початкову синхронізацію подань через їхнього єдиного власника.
     */
    public void loadViews() {
        if (neuronViewSynchronizer == null) {
            throw new IllegalStateException(
                    "Presentation actions must be configured before loading views"
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
     * Передає підлеглим контролерам запит на оновлення лише подань зв'язків.
     */
    private void refreshRelatedViews() {
        if (relatedViewRefresher == null) {
            throw new IllegalStateException(
                    "Presentation actions must be configured before refreshing related views"
            );
        }
        relatedViewRefresher.run();
    }

    /**
     * Визначає, чи вибрано нейрон, не передаючи координатору презентації весь змінний стан редактора.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо нейрон входить до поточного вибору.
     */
    public boolean isNeuronSelected(String neuronId) {
        return neuronId != null && state.selectedNeuronIds().contains(neuronId);
    }

    /**
     * Передає запит на оновлення вигляду нейронів координатору презентації.
     */
    public void refreshVisuals() {
        if (neuronVisualRefresher == null) {
            throw new IllegalStateException(
                    "Presentation actions must be configured before refreshing neuron visuals"
            );
        }
        neuronVisualRefresher.run();
    }

    /**
     * Передає координатору презентації запит на узгодження положень візуальних накладок.
     */
    public void refreshOverlayPositions() {
        if (overlayPositionRefresher == null) {
            throw new IllegalStateException(
                    "Presentation actions must be configured before refreshing overlays"
            );
        }
        overlayPositionRefresher.run();
    }

    /**
     * Оновлює позицію контекстного меню, лише якщо воно відкрите для зазначеного нейрона.
     *
     * @param neuronView подання нейрона, відносно якого розташовується меню.
     */
    public void refreshContextMenuPosition(NeuronView neuronView) {
        if (neuronView != null
                && menuController.isVisible()
                && state.selectedNeuronForMenu() != null
                && state.selectedNeuronForMenu().equals(neuronView.model().id())) {
            menuController.refreshPosition(neuronView);
        }
    }

    /**
     * Передає спеціалізованому контролеру ручок обертання запит на оновлення їхнього положення.
     */
    public void refreshRotationHandlePosition() {
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
     * Передає запит на зміну напрямку нейрона контролеру контекстного меню.
     *
     * @param neuronId ідентифікатор нейрона, напрямок якого потрібно перемкнути.
     */
    public void toggleNeuronDirection(String neuronId) {
        menuController.toggleDirection(neuronId);
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
