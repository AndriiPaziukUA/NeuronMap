package com.example.neuronmap.controller;

import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import com.example.neuronmap.view.support.SceneKeyboardModality;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Єдиний власник клавіатурних команд редактора та їхнього життєвого циклу. */
public final class EditorKeyboardController {

    private final BooleanSupplier undo;
    private final BooleanSupplier redo;
    private final BooleanSupplier deleteSelection;
    private final Runnable cancelInteractions;
    private final Runnable toggleSimulationPause;
    private final BooleanSupplier simulationPauseShortcutAvailable;
    private final Node simulationPauseShortcutAnchor;
    private final BooleanSupplier priorityEscapeModeActive;
    private Runnable continueSimulationOneTick = () -> { };
    private BooleanSupplier simulationStepShortcutAvailable = () -> false;
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private final EventHandler<KeyEvent> spaceKeyFilter = this::handleSpaceKeyPressed;
    private final EventHandler<KeyEvent> priorityEscapeFilter = this::handlePriorityEscape;
    private final EventHandler<KeyEvent> keyReleasedHandler = this::handleKeyReleased;
    private Scene scene;
    private boolean spacePressed;

    /**
     * Створює маршрутизатор базових гарячих клавіш редактора без команди паузи симуляції.
     * Цей конструктор збережений для компонентів, яким не потрібна клавіатурна команда симуляції.
     *
     * @param undo дія скасування останньої зміни.
     * @param redo дія повторення скасованої зміни.
     * @param deleteSelection дія видалення вибраних нейронів.
     * @param cancelInteractions дія скасування активної взаємодії.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    public EditorKeyboardController(
            BooleanSupplier undo,
            BooleanSupplier redo,
            BooleanSupplier deleteSelection,
            Runnable cancelInteractions
    ) {
        this.undo = Objects.requireNonNull(undo, "undo");
        this.redo = Objects.requireNonNull(redo, "redo");
        this.deleteSelection = Objects.requireNonNull(deleteSelection, "deleteSelection");
        this.cancelInteractions = Objects.requireNonNull(cancelInteractions, "cancelInteractions");
        toggleSimulationPause = () -> { };
        simulationPauseShortcutAvailable = () -> false;
        simulationPauseShortcutAnchor = null;
        priorityEscapeModeActive = () -> false;
    }

    /**
     * Створює маршрутизатор клавіатурних команд редактора, включно з умовною командою паузи симуляції.
     *
     * @param undo дія скасування останньої зміни.
     * @param redo дія повторення скасованої зміни.
     * @param deleteSelection дія видалення вибраних нейронів.
     * @param cancelInteractions дія скасування активної взаємодії.
     * @param toggleSimulationPause дія перемикання ручної паузи симуляції.
     * @param simulationPauseShortcutAvailable перевірка доступності команди паузи, коли симуляція активна.
     * @param simulationPauseShortcutAnchor вузол, що належить сцені редактора й визначає її межу.
     * @throws NullPointerException якщо будь-яку залежність не задано.
     */
    public EditorKeyboardController(
            BooleanSupplier undo,
            BooleanSupplier redo,
            BooleanSupplier deleteSelection,
            Runnable cancelInteractions,
            Runnable toggleSimulationPause,
            BooleanSupplier simulationPauseShortcutAvailable,
            Node simulationPauseShortcutAnchor
    ) {
        this(undo, redo, deleteSelection, cancelInteractions, toggleSimulationPause,
                simulationPauseShortcutAvailable, simulationPauseShortcutAnchor, () -> false);
    }

    /**
     * Створює маршрутизатор команд із пріоритетним обробленням Esc для активного інструмента створення.
     * Фільтр скасовує додавання нейронів або створення зв’язків до того, як Esc отримає головне меню.
     *
     * @param undo дія скасування останньої зміни.
     * @param redo дія повторення скасованої зміни.
     * @param deleteSelection дія видалення вибраних нейронів.
     * @param cancelInteractions дія скасування активної взаємодії.
     * @param toggleSimulationPause дія перемикання ручної паузи симуляції.
     * @param simulationPauseShortcutAvailable перевірка доступності команди паузи симуляції.
     * @param simulationPauseShortcutAnchor вузол, що належить сцені редактора й визначає її межу.
     * @param priorityEscapeModeActive перевірка, чи активний режим із пріоритетним скасуванням через Esc.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     */
    public EditorKeyboardController(
            BooleanSupplier undo,
            BooleanSupplier redo,
            BooleanSupplier deleteSelection,
            Runnable cancelInteractions,
            Runnable toggleSimulationPause,
            BooleanSupplier simulationPauseShortcutAvailable,
            Node simulationPauseShortcutAnchor,
            BooleanSupplier priorityEscapeModeActive
    ) {
        this.undo = Objects.requireNonNull(undo, "undo");
        this.redo = Objects.requireNonNull(redo, "redo");
        this.deleteSelection = Objects.requireNonNull(deleteSelection, "deleteSelection");
        this.cancelInteractions = Objects.requireNonNull(cancelInteractions, "cancelInteractions");
        this.toggleSimulationPause = Objects.requireNonNull(toggleSimulationPause, "toggleSimulationPause");
        this.simulationPauseShortcutAvailable = Objects.requireNonNull(
                simulationPauseShortcutAvailable, "simulationPauseShortcutAvailable");
        this.simulationPauseShortcutAnchor = Objects.requireNonNull(
                simulationPauseShortcutAnchor, "simulationPauseShortcutAnchor");
        this.priorityEscapeModeActive = Objects.requireNonNull(
                priorityEscapeModeActive, "priorityEscapeModeActive");
    }

    /**
     * Налаштовує команду Ctrl+Space для однотактного продовження, не змінюючи інші гарячі клавіші.
     *
     * @param action дія, що виконує один такт симуляції.
     * @param available перевірка, чи активні умови для виконання однотактної команди.
     * @throws NullPointerException якщо одну з залежностей не задано.
     */
    public void configureSimulationStepShortcut(Runnable action, BooleanSupplier available) {
        continueSimulationOneTick = Objects.requireNonNull(action, "action");
        simulationStepShortcutAvailable = Objects.requireNonNull(available, "available");
    }

    /**
     * Реєструє гарячі клавіші на сцені, попередньо від'єднавши їх від іншої сцени за потреби.
     * Пробіл перехоплюється на фазі фільтрації до стандартної активації фокусованих кнопок.
     * Повторне встановлення на ту саму сцену не створює дубльованих підписок.
     *
     * @param targetScene сцена, на якій потрібно обробляти клавіатурні команди.
     */
    public void install(Scene targetScene) {
        Objects.requireNonNull(targetScene, "targetScene");
        if (scene == targetScene) {
            return;
        }
        dispose();
        scene = targetScene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, priorityEscapeFilter);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, spaceKeyFilter);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleasedHandler);
    }

    /** Знімає обробники клавіатурних подій із поточної сцени; повторний виклик безпечний. */
    public void dispose() {
        if (scene == null) {
            return;
        }
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, priorityEscapeFilter);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, spaceKeyFilter);
        scene.removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        scene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        scene = null;
        spacePressed = false;
    }

    /** Перехоплює Esc до фільтра меню, коли активний інструмент потребує пріоритетного скасування. */
    private void handlePriorityEscape(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE || !priorityEscapeModeActive.getAsBoolean()) {
            return;
        }
        cancelInteractions.run();
        event.consume();
    }

    /** Перехоплює пробіл до того, як подання сфокусованої кнопки зможе активувати її замість симуляції. */
    private void handleSpaceKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            handleSpacePressed(event);
        }
    }

    /** Маршрутизує клавіші редактора, зберігаючи стандартне введення в текстових полях і модальних поданнях. */
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            return;
        }
        if (isTextInputTarget(event.getTarget())
                || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                || SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())) {
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.Z) {
            consumeWhenHandled(event, undo.getAsBoolean());
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.Y) {
            consumeWhenHandled(event, redo.getAsBoolean());
            return;
        }
        if (event.getCode() == KeyCode.DELETE) {
            consumeWhenHandled(event, deleteSelection.getAsBoolean());
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            cancelInteractions.run();
            event.consume();
        }
    }

    /**
     * Обробляє пробіл для ручної паузи та Ctrl+Space для однотактного продовження.
     * Комбінація Ctrl+Space не перемикає ручну паузу й маршрутизується лише за окремим дозволом.
     *
     * @param event подія натискання пробілу.
     */
    private void handleSpacePressed(KeyEvent event) {
        if (spacePressed) {
            return;
        }
        spacePressed = true;
        if (isControlSpace(event)) {
            if (canRouteSimulationShortcut(event) && simulationStepShortcutAvailable.getAsBoolean()) {
                continueSimulationOneTick.run();
                event.consume();
            }
            return;
        }
        if (!isUnmodifiedSpace(event)
                || isTextInputTarget(event.getTarget())
                || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                || SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())
                || !isInSimulationPauseShortcutScene(event.getTarget())
                || !simulationPauseShortcutAvailable.getAsBoolean()) {
            return;
        }
        toggleSimulationPause.run();
        event.consume();
    }

    /** Перевіряє Ctrl+Space без інших модифікаторів, щоб не перехоплювати альтернативні комбінації.
     *
     * @param event клавіатурна подія для перевірки.
     * @return {@code true}, якщо натиснуто лише Ctrl+Space.
     */
    private boolean isControlSpace(KeyEvent event) {
        return event.getCode() == KeyCode.SPACE
                && event.isControlDown()
                && !event.isShiftDown()
                && !event.isAltDown()
                && !event.isMetaDown();
    }

    /** Перевіряє, що однотактну команду можна обробляти в цільовій області редактора.
     *
     * @param event клавіатурна подія для перевірки.
     * @return {@code true}, якщо ціль події належить редактору та не заблокована модальним поданням.
     */
    private boolean canRouteSimulationShortcut(KeyEvent event) {
        return !isTextInputTarget(event.getTarget())
                && !EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())
                && !SceneKeyboardModality.hasActiveBoundaryFor(event.getTarget())
                && isInSimulationPauseShortcutScene(event.getTarget());
    }

    /** Скидає стан пробілу після відпускання, щоб автоповтор клавіші не запускав команди кілька разів. */
    private void handleKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            spacePressed = false;
        }
    }

    /** Перевіряє, що ціль події належить тій самій сцені, що й якір маршрутизатора. */
    private boolean isInSimulationPauseShortcutScene(Object target) {
        if (!(target instanceof Node targetNode) || simulationPauseShortcutAnchor == null) {
            return false;
        }
        return simulationPauseShortcutAnchor.getScene() != null
                && targetNode.getScene() == simulationPauseShortcutAnchor.getScene();
    }

    /** Перевіряє, що пробіл натиснуто без Shift, Ctrl, Alt або Meta. */
    private boolean isUnmodifiedSpace(KeyEvent event) {
        return !event.isShiftDown() && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown();
    }

    /** Позначає подію обробленою лише тоді, коли відповідна команда справді виконалася. */
    private void consumeWhenHandled(KeyEvent event, boolean handled) {
        if (handled) {
            event.consume();
        }
    }

    /** Перевіряє, чи спрямована подія на текстове поле або його вкладений вузол. */
    private boolean isTextInputTarget(Object target) {
        return JavaFxNodeLookup.findAncestor(target, TextInputControl.class) != null;
    }
}
