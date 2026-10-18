package com.example.neuronmap.view.support;

import javafx.scene.Node;

/**
 * Позначає вузол інтерфейсу, всередині якого глобальні команди редактора не повинні виконуватися.
 *
 * <p>Контракт дає модальним поданням змогу ізолювати клавіатурну взаємодію, не змушуючи контролери
 * залежати від конкретного класу подання або назви його CSS-класу.</p>
 */
public interface EditorKeyboardShortcutBoundary {

    /**
     * Визначає, чи має подання блокувати глобальні клавіатурні команди редактора.
     *
     * @return {@code true}, якщо команди редактора не повинні виходити за межі цього подання.
     */
    boolean blocksEditorShortcuts();

    /**
     * Перевіряє ціль клавіатурної події та її предків на активну межу ізоляції команд.
     *
     * @param target ціль події JavaFX.
     * @return {@code true}, якщо ціль належить поданню, яке блокує глобальні команди редактора.
     */
    static boolean blocksShortcutsFor(Object target) {
        if (!(target instanceof Node targetNode)) {
            return false;
        }
        Node current = targetNode;
        while (current != null) {
            if (current instanceof EditorKeyboardShortcutBoundary boundary
                    && boundary.blocksEditorShortcuts()) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
