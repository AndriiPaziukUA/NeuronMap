package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.util.WorldCoordinateMapper;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.WindowEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Обробляє команди копіювання й вставлення та керує життєвим циклом підписок буфера обміну. */
public final class NeuronClipboardController {

    private final NeuronClipboardService clipboardService;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable hideMenu;
    private final Runnable refreshPresentation;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private final EventHandler<WindowEvent> windowHiddenHandler = event -> clearClipboard();
    private final EventHandler<MouseEvent> mouseEnteredHandler = this::updateMousePosition;
    private final EventHandler<MouseEvent> mouseExitedHandler = event -> mouseInsideWorkspace = false;
    private final EventHandler<MouseEvent> mouseMovedHandler = this::updateMousePosition;
    private final ChangeListener<Scene> sceneChangeListener = this::handleSceneChanged;

    private Scene installedScene;
    private boolean disposed;
    private boolean mouseInsideWorkspace;
    private double lastMouseX;
    private double lastMouseY;

    /**
     * Створює обробник команд буфера обміну та одразу підключає потрібні подієві підписки.
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
        this.clipboardService = java.util.Objects.requireNonNull(clipboardService, "clipboardService");
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.hideMenu = java.util.Objects.requireNonNull(hideMenu, "hideMenu");
        this.refreshPresentation = java.util.Objects.requireNonNull(refreshPresentation, "refreshPresentation");
        this.save = java.util.Objects.requireNonNull(save, "save");
        this.status = java.util.Objects.requireNonNull(status, "status");
        this.localization = java.util.Objects.requireNonNull(localization, "localization");

        installMouseTracking();
        workspace.node().sceneProperty().addListener(sceneChangeListener);
        attachSceneHandlers(workspace.node().getScene());
    }

    /** Очищає лише вміст буфера копіювання, не змінюючи подієві реєстрації контролера. */
    public void clearClipboard() {
        clipboardService.clear();
    }

    /** Зберігає сумісність з попереднім API очищення вмісту буфера. */
    public void clear() {
        clearClipboard();
    }

    /** Знімає всі подієві підписки та очищає тимчасовий стан контролера; повторні виклики безпечні. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        workspace.node().sceneProperty().removeListener(sceneChangeListener);
        detachSceneHandlers(installedScene);
        installedScene = null;
        workspace.node().removeEventFilter(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        workspace.node().removeEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        workspace.node().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        mouseInsideWorkspace = false;
        lastMouseX = 0.0;
        lastMouseY = 0.0;
        clearClipboard();
    }

    /** Реєструє стабільні обробники подій для відстеження положення курсора. */
    private void installMouseTracking() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_ENTERED, mouseEnteredHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        workspace.node().addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
    }

    /** Зберігає поточні координати курсора для вставлення нейронів біля нього. */
    private void updateMousePosition(MouseEvent event) {
        if (disposed) {
            return;
        }
        Point2D point = workspace.node().sceneToLocal(event.getSceneX(), event.getSceneY());
        lastMouseX = point.getX();
        lastMouseY = point.getY();
        mouseInsideWorkspace = true;
    }

    /** Переносить клавіатурні та віконні обробники на фактичну поточну сцену. */
    private void handleSceneChanged(
            javafx.beans.value.ObservableValue<? extends Scene> observable,
            Scene oldScene,
            Scene newScene
    ) {
        detachSceneHandlers(installedScene);
        installedScene = null;
        attachSceneHandlers(newScene);
    }

    /** Під'єднує обробники клавіатури й приховування до переданої сцени, якщо вона існує. */
    private void attachSceneHandlers(Scene scene) {
        if (disposed || scene == null || scene == installedScene) {
            return;
        }
        detachSceneHandlers(installedScene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        scene.addEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
        installedScene = scene;
    }

    /** Симетрично прибирає обробники зі сцени, на якій вони були фактично зареєстровані. */
    private void detachSceneHandlers(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        scene.removeEventHandler(WindowEvent.WINDOW_HIDDEN, windowHiddenHandler);
    }

    /** Обробляє Ctrl+C і Ctrl+V, не втручаючись у роботу текстових полів. */
    private void handleKeyPressed(KeyEvent event) {
        if (disposed || isTextInputTarget(event.getTarget()) || !event.isControlDown()) {
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

    /** Копіює поточний вибір нейронів у внутрішній буфер зі збереженням зв'язків і взаємного розташування. */
    private boolean copySelection() {
        Set<String> selectedIds = new LinkedHashSet<>(state.selectedNeuronIds());
        if (selectedIds.isEmpty()) {
            String menuId = state.selectedNeuronForMenu();
            if (menuId != null) {
                selectedIds.add(menuId);
            }
        }
        if (selectedIds.isEmpty()) {
            clearClipboard();
            status.accept(localization.text("status.copy_none"));
            return false;
        }
        if (!clipboardService.copy(selectedIds)) {
            return false;
        }
        NeuronClipboardService.ClipboardContent content = clipboardService.content().orElseThrow();
        status.accept(content.grouped()
                ? localization.text("status.copy_group")
                : content.items().size() == 1
                ? localization.text("status.copy_neuron")
                : localization.text("status.copy_selection"));
        return true;
    }

    /** Вставляє буфер у позицію курсора й синхронізує презентацію один раз після успішної вставки. */
    private boolean pasteAtMouse() {
        if (disposed || !mouseInsideWorkspace || !clipboardService.hasContent()) {
            return false;
        }
        Point2D worldPoint = WorldCoordinateMapper.viewportToWorld(
                new Point2D(lastMouseX, lastMouseY), state.zoom(), state.panX(), state.panY());
        hideMenu.run();
        state.clearSelection();
        Point2D neuronTopLeft = NeuronVisualGeometry.topLeftFromCenter(worldPoint);
        List<Neuron> pasted = clipboardService.paste(neuronTopLeft.getX(), neuronTopLeft.getY());
        if (pasted.isEmpty()) {
            return false;
        }
        state.clearSelection();
        pasted.forEach(neuron -> state.toggleSelection(neuron.id()));
        refreshPresentation.run();
        save.run();
        status.accept(pasted.size() == 1
                ? localization.text("status.paste_neuron")
                : localization.text("status.paste_neurons"));
        return true;
    }

    /** Перевіряє, чи перебуває фокус у текстовому полі, де команди копіювання не має перехоплювати редактор. */
    private boolean isTextInputTarget(Object target) {
        return JavaFxNodeLookup.findAncestor(target, TextInputControl.class) != null;
    }
}
