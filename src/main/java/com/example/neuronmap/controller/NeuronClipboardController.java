package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.WindowEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Обробляє копіювання й вставлення вибраних нейронів через буфер обміну та розміщує вставлені нейрони біля курсора.
 */
public final class NeuronClipboardController {

    private final NeuronClipboardService clipboardService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Consumer<Neuron> addView;
    private final Runnable refreshPresentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    private final EventHandler<KeyEvent> keyHandler;
    private final EventHandler<WindowEvent> windowHiddenHandler;

    private boolean mouseInsideWorkspace;
    private double lastMouseX;
    private double lastMouseY;

    /**
     * Створює екземпляр NeuronClipboardController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param clipboardService служба копіювання та вставлення нейронів.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param hideMenu callback для приховування контекстного меню.
     * @param addView callback, що додає візуальне подання нового нейрона.
     * @param refreshPresentation callback, який синхронізує візуальні подання з моделлю.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     */
    public NeuronClipboardController(
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<Neuron> addView,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                clipboardService,
                state,
                workspace,
                hideMenu,
                addView,
                refreshPresentation,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює екземпляр NeuronClipboardController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param clipboardService служба копіювання та вставлення нейронів.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param workspace полотно редактора.
     * @param hideMenu callback для приховування контекстного меню.
     * @param addView callback, що додає візуальне подання нового нейрона.
     * @param refreshPresentation callback, який синхронізує візуальні подання з моделлю.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
     */
    public NeuronClipboardController(
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Consumer<Neuron> addView,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.clipboardService = clipboardService;
        this.state = state;
        this.workspace = workspace;
        this.hideMenu = hideMenu;
        this.addView = addView;
        this.refreshPresentation = refreshPresentation;
        this.save = save;
        this.status = status;
        this.localization = localization;
        this.keyHandler = this::handleKeyPressed;
        this.windowHiddenHandler = event -> this.clipboardService.clear();

        installMouseTracking();
        installSceneKeyHandler();
    }

    /**
     * Очищає  від тимчасових або застарілих значень.
     */
    public void clear() {
        clipboardService.clear();
    }

    /**
     * Реєструє обробники подій, потрібні для mouse tracking.
     */
    private void installMouseTracking() {
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_ENTERED,
                this::updateMousePosition
        );
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_EXITED,
                event -> mouseInsideWorkspace = false
        );
        workspace.node().addEventFilter(
                MouseEvent.MOUSE_MOVED,
                this::updateMousePosition
        );
    }

    /**
     * Зберігає поточні координати курсора, щоб вставити скопійовані нейрони біля його положення.
     *
     * @param event подія інтерфейсу, яку потрібно обробити.
     */
    private void updateMousePosition(MouseEvent event) {
        Point2D point = workspace.node().sceneToLocal(
                event.getSceneX(),
                event.getSceneY()
        );
        lastMouseX = point.getX();
        lastMouseY = point.getY();
        mouseInsideWorkspace = true;
    }

    /**
     * Реєструє обробник клавіш сцени для команд копіювання та вставлення нейронів.
     */
    private void installSceneKeyHandler() {
        workspace.node().sceneProperty().addListener(
                (observable, oldScene, newScene) -> {
                    if (oldScene != null) {
                        oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                        oldScene.removeEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
                    }

                    if (newScene != null) {
                        newScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                        newScene.addEventHandler(
                                WindowEvent.WINDOW_HIDDEN,
                                windowHiddenHandler
                        );
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
        if (isTextInputTarget(event.getTarget()) || !event.isControlDown()) {
            return;
        }

        if (event.getCode() == KeyCode.C) {
            if (copySelection()) {
                event.consume();
            }
            return;
        }

        if (event.getCode() == KeyCode.V && pasteAtMouse()) {
            event.consume();
        }
    }

    /**
     * Копіює поточний вибір нейронів у внутрішній буфер, зберігаючи їхні налаштування й взаємне розташування.
     */
    private boolean copySelection() {
        Set<String> selectedIds = new LinkedHashSet<>(
                state.selectedNeuronIds()
        );

        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }

        if (selectedIds.isEmpty()) {
            clipboardService.clear();
            status.accept(localization.text("status.copy_none"));
            return false;
        }

        if (!clipboardService.copy(selectedIds)) {
            return false;
        }

        NeuronClipboardService.ClipboardContent content =
                clipboardService.content().orElseThrow();

        status.accept(
                content.grouped()
                        ? localization.text("status.copy_group")
                        : content.items().size() == 1
                        ? localization.text("status.copy_neuron")
                        : localization.text("status.copy_selection")
        );
        return true;
    }

    /**
     * Вставляє вміст буфера в позицію курсора та оновлює відображення карти.
     */
    private boolean pasteAtMouse() {
        if (!mouseInsideWorkspace || !clipboardService.hasContent()) {
            return false;
        }

        double worldX = (lastMouseX - state.panX()) / state.zoom();
        double worldY = (lastMouseY - state.panY()) / state.zoom();

        hideMenu.run();
        state.clearSelection();

        List<Neuron> pasted = clipboardService.paste(
                worldX - NeuronView.WIDTH / 2.0,
                worldY - NeuronView.HEIGHT / 2.0
        );

        for (Neuron neuron : pasted) {
            addView.accept(neuron);
        }

        state.clearSelection();
        pasted.forEach(neuron -> state.toggleSelection(neuron.id()));

        refreshPresentation.run();
        save.run();

        status.accept(
                pasted.size() == 1
                        ? localization.text("status.paste_neuron")
                        : localization.text("status.paste_neurons")
        );
        return !pasted.isEmpty();
    }

    /**
     * Перевіряє, чи перебуває фокус у текстовому полі, де клавіатурні команди копіювання не мають перехоплюватися редактором.
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
