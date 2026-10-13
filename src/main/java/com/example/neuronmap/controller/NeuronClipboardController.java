package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.util.WorldCoordinateMapper;
import com.example.neuronmap.view.JavaFxNodeLookup;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
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
 * Обробляє команди копіювання й вставлення, відстежує курсор і делегує побудову подань координатору презентації.
 */
public final class NeuronClipboardController {

    private final NeuronClipboardService clipboardService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
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
     * Створює обробник команд буфера обміну без прямої залежності від створення візуальних подань.
     *
     * @param clipboardService служба копіювання та вставлення нейронів.
     * @param state стан вибору й координат камери редактора.
     * @param workspace робоча область для відстеження курсора.
     * @param hideMenu дія приховування контекстного меню.
     * @param refreshPresentation дія синхронізації візуальних подань з моделлю.
     * @param save дія збереження зміненої карти.
     * @param status дія показу повідомлень.
     * @param localization служба локалізації.
     */
    public NeuronClipboardController(
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Runnable hideMenu,
            Runnable refreshPresentation,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.clipboardService = java.util.Objects.requireNonNull(
                clipboardService,
                "clipboardService"
        );
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
        this.keyHandler = this::handleKeyPressed;
        this.windowHiddenHandler = event -> this.clipboardService.clear();

        installMouseTracking();
        installSceneKeyHandler();
    }

    /**
     * Очищає внутрішній буфер копіювання нейронів.
     */
    public void clear() {
        clipboardService.clear();
    }

    /**
     * Реєструє обробники подій, потрібні для відстеження положення курсора.
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
     * Зберігає поточні координати курсора для вставлення нейронів біля нього.
     *
     * @param event подія миші, з якої потрібно взяти координати.
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
     * Обробляє Ctrl+C і Ctrl+V, не втручаючись у роботу текстових полів.
     *
     * @param event подія натискання клавіші.
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
     * Копіює поточний вибір нейронів у внутрішній буфер зі збереженням зв'язків і взаємного розташування.
     *
     * @return {@code true}, якщо вміст скопійовано.
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
     * Вставляє весь вміст буфера в позицію курсора й виконує одну синхронізацію презентації після вставлення.
     *
     * @return {@code true}, якщо вставлено принаймні один нейрон.
     */
    private boolean pasteAtMouse() {
        if (!mouseInsideWorkspace || !clipboardService.hasContent()) {
            return false;
        }

        Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                new Point2D(lastMouseX, lastMouseY),
                state.zoom(),
                state.panX(),
                state.panY()
        );

        hideMenu.run();
        state.clearSelection();

        List<Neuron> pasted = clipboardService.paste(
                worldPoint.getX() - NeuronView.WIDTH / 2.0,
                worldPoint.getY() - NeuronView.HEIGHT / 2.0
        );
        if (pasted.isEmpty()) {
            return false;
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
        return true;
    }

    /**
     * Перевіряє, чи перебуває фокус у текстовому полі, де команди копіювання не мають перехоплюватися редактором.
     *
     * @param target цільовий вузол JavaFX, що отримав клавіатурну подію.
     * @return {@code true}, якщо фокус перебуває в текстовому полі.
     */
    private boolean isTextInputTarget(Object target) {
        return JavaFxNodeLookup.findAncestor(target, TextInputControl.class) != null;
    }
}
