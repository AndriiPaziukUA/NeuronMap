package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.StructureService;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Обробляє взаємодію з нейронами, перемикання шухлядок структур та закріплення їхніх внутрішніх елементів. */
public final class NeuronInteractionController {

    private final EditorState state;
    private final SelectionController selectionController;
    private final SimulationController simulation;
    private final LocalizationService localization;
    private final StructureService structureService;
    private final Runnable save;
    private NeuronClipboardController clipboardController;
    private NeuronDeletionController deletionController;
    private NeuronMenuController menuController;
    private NeuronRotationController rotationController;
    private NeuronDragController dragController;
    private boolean controllersInitialized;
    private final Map<NeuronView, NeuronHandlers> neuronHandlers = new IdentityHashMap<>();
    private Runnable neuronViewSynchronizer;
    private Runnable neuronVisualRefresher;
    private Runnable overlayPositionRefresher;
    private Runnable relatedViewRefresher;
    private boolean disposed;

    /**
     * Створює основу контролера та зберігає відповідальні служби для зміни стану структур.
     * Вузька фабрика ін'єктує спеціалізовані контролери після створення цього callback-власника.
     *
     * @param state спільний стан редактора.
     * @param selectionController контролер вибору нейронів.
     * @param simulation контролер симуляції.
     * @param localization спільна служба локалізації інтерфейсу.
     * @param structureService служба зміни членства та стану структур.
     * @param save дія збереження змін карти й фіксації стану історії.
     * @throws NullPointerException якщо обов'язкова залежність відсутня.
     */
    NeuronInteractionController(
            EditorState state,
            SelectionController selectionController,
            SimulationController simulation,
            LocalizationService localization,
            StructureService structureService,
            Runnable save
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.selectionController = Objects.requireNonNull(selectionController, "selectionController");
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.structureService = Objects.requireNonNull(structureService, "structureService");
        this.save = Objects.requireNonNull(save, "save");
    }

    /**
     * Одноразово встановлює створені фабрикою спеціалізовані контролери взаємодії.
     *
     * @param clipboardController контролер копіювання та вставлення нейронів.
     * @param deletionController контролер видалення нейронів.
     * @param menuController контролер контекстного меню нейрона.
     * @param rotationController контролер обертання нейронів.
     * @param dragController контролер перетягування нейронів.
     * @throws NullPointerException якщо будь-який підконтролер не передано.
     * @throws IllegalStateException якщо підконтролери вже були встановлені.
     */
    void initializeControllers(
            NeuronClipboardController clipboardController,
            NeuronDeletionController deletionController,
            NeuronMenuController menuController,
            NeuronRotationController rotationController,
            NeuronDragController dragController
    ) {
        if (controllersInitialized) {
            throw new IllegalStateException("Neuron interaction controllers are already initialized");
        }
        this.clipboardController = Objects.requireNonNull(clipboardController, "clipboardController");
        this.deletionController = Objects.requireNonNull(deletionController, "deletionController");
        this.menuController = Objects.requireNonNull(menuController, "menuController");
        this.rotationController = Objects.requireNonNull(rotationController, "rotationController");
        this.dragController = Objects.requireNonNull(dragController, "dragController");
        controllersInitialized = true;
    }

    /** Реєструє дії координатора презентації, який є єдиним власником набору графічних подань. */
    public void setPresentationActions(
            Runnable synchronizer,
            Runnable visualRefresher,
            Runnable overlayPositionRefresher,
            Runnable relatedViewRefresher
    ) {
        ensureNotDisposed();
        if (neuronViewSynchronizer != null || neuronVisualRefresher != null
                || this.overlayPositionRefresher != null || this.relatedViewRefresher != null) {
            throw new IllegalStateException("Presentation actions are already configured");
        }
        neuronViewSynchronizer = Objects.requireNonNull(synchronizer, "synchronizer");
        neuronVisualRefresher = Objects.requireNonNull(visualRefresher, "visualRefresher");
        this.overlayPositionRefresher = Objects.requireNonNull(overlayPositionRefresher, "overlayPositionRefresher");
        this.relatedViewRefresher = Objects.requireNonNull(relatedViewRefresher, "relatedViewRefresher");
    }

    /** Забезпечує початкову синхронізацію подань через їхнього єдиного власника. */
    public void loadViews() {
        ensureNotDisposed();
        if (neuronViewSynchronizer == null) {
            throw new IllegalStateException("Presentation actions must be configured before loading views");
        }
        neuronViewSynchronizer.run();
    }

    /**
     * Встановлює обробники подій на поданні нейрона, ручці обертання та клавіатурному фокусі.
     * Enter обробляється лише тоді, коли фокус належить самому поданню, а не вкладеному елементу.
     * Повторне встановлення на те саме подання не створює дубльованих обробників.
     *
     * @param neuronView подання нейрона, події якого обробляються.
     * @param rotationHandle пов'язана ручка обертання.
     * @throws NullPointerException якщо подання або ручку не задано.
     * @throws IllegalStateException якщо контролер уже звільнив свої ресурси.
     */
    public void installInteractionHandlers(NeuronView neuronView, RotationHandleView rotationHandle) {
        ensureNotDisposed();
        Objects.requireNonNull(neuronView, "neuronView");
        Objects.requireNonNull(rotationHandle, "rotationHandle");
        if (neuronHandlers.containsKey(neuronView)) {
            return;
        }
        rotationController.installHandleHandlers(neuronView, rotationHandle);
        neuronView.configureParameterTooltipLocalization(localization);
        neuronView.setFocusTraversable(true);
        EventHandler<MouseEvent> pressed = event -> dragController.handlePressed(neuronView, event);
        EventHandler<MouseEvent> dragged = event -> {
            if (neuronView.isStructureMember()) {
                event.consume();
            } else {
                dragController.handleDragged(neuronView, event);
            }
        };
        EventHandler<MouseEvent> released = event -> {
            if (neuronView.isStructureMember()) {
                event.consume();
            } else {
                dragController.handleReleased(neuronView);
            }
        };
        EventHandler<MouseEvent> clicked = event -> handleNeuronClicked(neuronView, event);
        EventHandler<KeyEvent> keyPressed = event -> handleNeuronKeyPressed(neuronView, event);
        neuronView.addEventHandler(MouseEvent.MOUSE_PRESSED, pressed);
        neuronView.addEventHandler(MouseEvent.MOUSE_DRAGGED, dragged);
        neuronView.addEventHandler(MouseEvent.MOUSE_RELEASED, released);
        neuronView.addEventHandler(MouseEvent.MOUSE_CLICKED, clicked);
        neuronView.addEventHandler(KeyEvent.KEY_PRESSED, keyPressed);
        neuronHandlers.put(neuronView,
                new NeuronHandlers(rotationHandle, pressed, dragged, released, clicked, keyPressed));
    }

    /**
     * Від'єднує обробники миші й клавіатури та підказку перед вилученням подання з карти.
     *
     * @param neuronView подання нейрона; {@code null} дозволено, якщо потрібно зняти лише ручку.
     * @param rotationHandle пов'язана ручка обертання, якщо її потрібно від'єднати окремо.
     */
    public void uninstallInteractionHandlers(NeuronView neuronView, RotationHandleView rotationHandle) {
        NeuronHandlers handlers = neuronView == null ? null : neuronHandlers.remove(neuronView);
        if (handlers != null) {
            dragController.cancelDragging(neuronView);
            neuronView.removeEventHandler(MouseEvent.MOUSE_PRESSED, handlers.pressedHandler());
            neuronView.removeEventHandler(MouseEvent.MOUSE_DRAGGED, handlers.draggedHandler());
            neuronView.removeEventHandler(MouseEvent.MOUSE_RELEASED, handlers.releasedHandler());
            neuronView.removeEventHandler(MouseEvent.MOUSE_CLICKED, handlers.clickedHandler());
            neuronView.removeEventHandler(KeyEvent.KEY_PRESSED, handlers.keyPressedHandler());
            neuronView.setFocusTraversable(false);
            neuronView.disposeParameterTooltip();
            rotationController.uninstallHandleHandlers(handlers.rotationHandle());
        } else if (rotationHandle != null) {
            rotationController.uninstallHandleHandlers(rotationHandle);
        }
    }

    /** Перевіряє, чи має подію обробляти вкладений інтерактивний елемент, а не полотно. */
    public boolean isInteractiveTarget(Object target) {
        ensureControllersInitialized();
        return JavaFxNodeLookup.findAncestorMatching(target,
                node -> node instanceof NeuronView
                        || node instanceof RotationHandleView
                        || node == menuController.node()
                        || node instanceof Control) != null;
    }

    /** Передає підлеглим контролерам запит на оновлення лише подань зв'язків. */
    void refreshRelatedViews() {
        ensurePresentationActions(relatedViewRefresher,
                "Presentation actions must be configured before refreshing related views");
        relatedViewRefresher.run();
    }

    /** Перевіряє, чи входить нейрон до поточного вибору. */
    public boolean isNeuronSelected(String neuronId) {
        return neuronId != null && state.selectedNeuronIds().contains(neuronId);
    }

    /** Передає запит на оновлення вигляду нейронів координатору презентації. */
    public void refreshVisuals() {
        ensurePresentationActions(neuronVisualRefresher,
                "Presentation actions must be configured before refreshing neuron visuals");
        neuronVisualRefresher.run();
    }

    /** Передає координатору презентації запит на узгодження положень візуальних накладок. */
    public void refreshOverlayPositions() {
        ensurePresentationActions(overlayPositionRefresher,
                "Presentation actions must be configured before refreshing overlays");
        overlayPositionRefresher.run();
    }

    /** Оновлює позицію контекстного меню, лише якщо воно відкрите для зазначеного нейрона. */
    public void refreshContextMenuPosition(NeuronView neuronView) {
        if (disposed || neuronView == null || !menuController.isVisible()
                || state.selectedNeuronForMenu() == null
                || !state.selectedNeuronForMenu().equals(neuronView.model().id())) {
            return;
        }
        menuController.refreshPosition(neuronView);
    }

    /** Передає спеціалізованому контролеру ручок обертання запит на оновлення їхнього положення. */
    public void refreshRotationHandlePosition() {
        if (!disposed) {
            rotationController.refreshPosition();
        }
    }

    /**
     * Відкриває контекстне меню та передає йому спільну дію перемикання шухлядки, якщо це структура.
     *
     * @param neuronView подання нейрона, для якого потрібно відкрити меню.
     * @throws NullPointerException якщо подання нейрона не задано.
     */
    public void showMenu(NeuronView neuronView) {
        ensureNotDisposed();
        Objects.requireNonNull(neuronView, "neuronView");
        rotationController.hideAll();
        Runnable structureDrawerToggleAction = neuronView.hasStructureDrawer()
                ? () -> toggleStructureDrawerAndRefresh(neuronView)
                : null;
        menuController.show(neuronView, structureDrawerToggleAction);
    }

    /** Передає запит на зміну напрямку нейрона контролеру контекстного меню. */
    public void toggleNeuronDirection(String neuronId) {
        ensureNotDisposed();
        menuController.toggleDirection(neuronId);
    }

    /** Приховує контекстне меню нейрона та всі ручки обертання. */
    public void hideMenu() {
        menuController.hide();
        rotationController.hideAll();
    }

    /** Передає видалення поточного вибору відповідному обробнику. */
    public boolean deleteSelectedNeurons() {
        ensureNotDisposed();
        return deletionController.deleteSelectedNeurons();
    }

    /** Знімає обробники миші й клавіатури, підказки та звільняє контролери взаємодії. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        menuController.dispose();
        rotationController.dispose();
        neuronHandlers.forEach((view, handlers) -> {
            dragController.cancelDragging(view);
            view.removeEventHandler(MouseEvent.MOUSE_PRESSED, handlers.pressedHandler());
            view.removeEventHandler(MouseEvent.MOUSE_DRAGGED, handlers.draggedHandler());
            view.removeEventHandler(MouseEvent.MOUSE_RELEASED, handlers.releasedHandler());
            view.removeEventHandler(MouseEvent.MOUSE_CLICKED, handlers.clickedHandler());
            view.removeEventHandler(KeyEvent.KEY_PRESSED, handlers.keyPressedHandler());
            view.setFocusTraversable(false);
            view.disposeParameterTooltip();
        });
        neuronHandlers.clear();
        clipboardController.dispose();
        state.clearTransientModes();
    }

    /**
     * Обробляє клацання по власному тілу нейрона, подвійним кліком активуючи нейрон або шухлядку.
     * Одинарний лівий клік передає клавіатурний фокус самому нейрону, а не його контейнеру-предку.
     *
     * @param neuronView подання, обробник якого отримав подію.
     * @param event подія клацання миші.
     */
    private void handleNeuronClicked(NeuronView neuronView, MouseEvent event) {
        if (disposed || JavaFxNodeLookup.findAncestor(event.getTarget(), NeuronView.class) != neuronView) {
            return;
        }
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
        neuronView.requestFocus();
        if (event.getClickCount() == 2 && !neuronView.wasDragged() && state.isIdle()) {
            if (neuronView.hasStructureDrawer()) {
                toggleStructureDrawerAndRefresh(neuronView);
            } else {
                simulation.emitPulse(neuronView.model().id());
            }
            event.consume();
            return;
        }
        if (neuronView.isStructureMember()) {
            event.consume();
            return;
        }
        if (!state.isIdle() || neuronView.wasDragged()) {
            return;
        }
        rotationController.show(neuronView.model().id());
        event.consume();
    }

    /**
     * Виконує команду Enter для нейрона, який сам є ціллю клавіатурної події.
     * Структура перемикає шухлядку тим самим сценарієм, що й контекстне меню; звичайний нейрон
     * запускає ручний імпульс через контролер симуляції. Текстові та модальні області не перехоплюються.
     *
     * @param neuronView подання, яке отримало клавіатурний фокус.
     * @param event подія натискання клавіші.
     */
    private void handleNeuronKeyPressed(NeuronView neuronView, KeyEvent event) {
        if (disposed || event.isConsumed() || event.getCode() != KeyCode.ENTER
                || event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown()
                || event.getTarget() != neuronView
                || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                || SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())
                || !state.isIdle()) {
            return;
        }
        if (neuronView.hasStructureDrawer()) {
            toggleStructureDrawerAndRefresh(neuronView);
        } else {
            rotationController.hideAll();
            simulation.emitPulse(neuronView.model().id());
        }
        event.consume();
    }

    /**
     * Перемикає шухлядку структури, після чого синхронізує залежні зв'язки та положення накладок.
     * Цей самий сценарій використовується кнопкою меню, подвійним клацанням і клавішею Enter.
     *
     * @param neuronView подання структури, шухлядку якої потрібно перемкнути.
     */
    private void toggleStructureDrawerAndRefresh(NeuronView neuronView) {
        if (toggleStructureDrawer(neuronView)) {
            refreshRelatedViews();
            refreshOverlayPositions();
        }
    }

    /**
     * Перемикає стан шухлядки через службу структури, синхронізує подання та зберігає карту.
     * Ручка обертання ховається до оновлення подання, щоб не залишатися над відкритою шухлядкою.
     *
     * @param neuronView подання структури, шухлядку якої потрібно перемкнути.
     * @return {@code true}, якщо стан існуючої структури було змінено.
     */
    private boolean toggleStructureDrawer(NeuronView neuronView) {
        String structureId = neuronView.model().id();
        var structure = structureService.find(structureId);
        if (structure.isEmpty()) {
            return false;
        }
        boolean expanded = !structure.orElseThrow().isExpanded();
        if (!structureService.setExpanded(structureId, expanded)) {
            return false;
        }
        rotationController.hideAll();
        if (neuronView.isStructureDrawerExpanded() != expanded) {
            neuronView.toggleStructureDrawer();
        }
        save.run();
        return true;
    }

    /** Перевіряє, що відповідна дія презентації вже була налаштована координатором-власником. */
    private void ensurePresentationActions(Runnable action, String message) {
        ensureNotDisposed();
        if (action == null) {
            throw new IllegalStateException(message);
        }
    }

    /** Перевіряє, чи фабрика завершила встановлення всіх спеціалізованих контролерів. */
    private void ensureControllersInitialized() {
        if (!controllersInitialized) {
            throw new IllegalStateException("Neuron interaction controllers must be initialized before use");
        }
    }

    /** Забороняє активувати контролер після завершення його життєвого циклу. */
    private void ensureNotDisposed() {
        ensureControllersInitialized();
        if (disposed) {
            throw new IllegalStateException("Neuron interaction controller is disposed");
        }
    }

    /** Зберігає подієві підписки кожного подання, щоб від'єднати їх разом із відповідною ручкою обертання. */
    private record NeuronHandlers(
            RotationHandleView rotationHandle,
            EventHandler<MouseEvent> pressedHandler,
            EventHandler<MouseEvent> draggedHandler,
            EventHandler<MouseEvent> releasedHandler,
            EventHandler<MouseEvent> clickedHandler,
            EventHandler<KeyEvent> keyPressedHandler
    ) { }
}
