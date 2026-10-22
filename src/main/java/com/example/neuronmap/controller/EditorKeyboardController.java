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

    /**
     * Перехоплює Esc до фільтра меню, коли активний інструмент потребує пріоритетного скасування.
     *
     * @param event подія натискання клавіші.
     */
    private void handlePriorityEscape(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE || !priorityEscapeModeActive.getAsBoolean()) {
            return;
        }
        cancelInteractions.run();
        event.consume();
    }

    /**
     * Перехоплює пробіл до того, як подання сфокусованої кнопки зможе активувати її замість симуляції.
     *
     * @param event подія натискання клавіші.
     */
    private void handleSpaceKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            handleSpacePressed(event);
        }
    }

    /**
     * Маршрутизує гарячі клавіші й зберігає стандартне введення у текстових елементах та модальних поданнях.
     *
     * @param event подія натискання клавіші.
     */
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
     * Виконує паузу лише для окремого натискання пробілу в доступному редакторі.
     * Пробіл працює по всій сцені, крім текстового введення, модальних меж і комбінацій із модифікаторами.
     *
     * @param event подія натискання пробілу.
     */
    private void handleSpacePressed(KeyEvent event) {
        if (spacePressed) {
            return;
        }
        spacePressed = true;
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

    /**
     * Скидає стан пробілу після відпускання, щоб автоповтор клавіші не перемикав паузу кілька разів.
     *
     * @param event подія відпускання клавіші.
     */
    private void handleKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            spacePressed = false;
        }
    }

    /**
     * Перевіряє, що ціль події належить тій самій сцені, що й якір маршрутизатора.
     * Фокус може залишатися на будь-якому елементі сцени, зокрема на кнопці панелі інструментів.
     *
     * @param target ціль клавіатурної події.
     * @return {@code true}, якщо ціль належить сцені редактора.
     */
    private boolean isInSimulationPauseShortcutScene(Object target) {
        if (!(target instanceof Node targetNode) || simulationPauseShortcutAnchor == null) {
            return false;
        }
        return simulationPauseShortcutAnchor.getScene() != null
                && targetNode.getScene() == simulationPauseShortcutAnchor.getScene();
    }

    /**
     * Перевіряє, що пробіл натиснуто без модифікаторів.
     *
     * @param event подія натискання пробілу.
     * @return {@code true}, якщо пробіл натиснуто без Shift, Ctrl, Alt або Meta.
     */
    private boolean isUnmodifiedSpace(KeyEvent event) {
        return !event.isShiftDown() && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown();
    }

    /**
     * Позначає подію обробленою лише тоді, коли відповідна команда справді виконалася.
     *
     * @param event клавіатурна подія, яку може бути потрібно спожити.
     * @param handled ознака успішного виконання команди.
     */
    private void consumeWhenHandled(KeyEvent event, boolean handled) {
        if (handled) {
            event.consume();
        }
    }

    /**
     * Перевіряє, чи спрямована подія на текстове поле або його вкладений вузол.
     *
     * @param target ціль клавіатурної події.
     * @return {@code true}, якщо ціллю є текстовий елемент введення або його нащадок.
     */
    private boolean isTextInputTarget(Object target) {
        return JavaFxNodeLookup.findAncestor(target, TextInputControl.class) != null;
    }
}
