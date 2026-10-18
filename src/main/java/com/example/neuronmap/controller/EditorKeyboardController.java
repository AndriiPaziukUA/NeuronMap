package com.example.neuronmap.controller;

import com.example.neuronmap.view.support.EditorKeyboardShortcutBoundary;
import com.example.neuronmap.view.support.JavaFxNodeLookup;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Єдиний власник клавіатурних команд редактора та їхнього життєвого циклу.
 * Передає команди історії, видаленню й скасуванню взаємодії відповідним компонентам.
 */
public final class EditorKeyboardController {

    private final BooleanSupplier undo;
    private final BooleanSupplier redo;
    private final BooleanSupplier deleteSelection;
    private final Runnable cancelInteractions;
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private Scene scene;

    /**
     * Створює маршрутизатор гарячих клавіш із діями, які належать відповідним компонентам редактора.
     *
     * @param undo дія скасування останньої зміни.
     * @param redo дія повторення скасованої зміни.
     * @param deleteSelection дія видалення вибраних нейронів.
     * @param cancelInteractions дія скасування активної взаємодії.
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
    }

    /**
     * Реєструє гарячі клавіші на сцені, попередньо від'єднавши їх від іншої сцени за потреби.
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
        scene.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
    }

    /** Знімає обробник клавіатурних подій із поточної сцени; повторний виклик безпечний. */
    public void dispose() {
        if (scene == null) {
            return;
        }
        scene.removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        scene = null;
    }

    /**
     * Маршрутизує підтримувані гарячі клавіші та зберігає стандартне введення у текстових полях.
     *
     * @param event подія натискання клавіші.
     */
    private void handleKeyPressed(KeyEvent event) {
        if (isTextInputTarget(event.getTarget()) || EditorKeyboardShortcutBoundary.blocksShortcutsFor(event.getTarget())) {
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
