package com.example.neuronmap.view.support;

import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Зберігає активні межі клавіатурної модальності для кожної сцени JavaFX.
 *
 * <p>Перевірка лише предків вузла не виявляє подій, спрямованих на фон редактора поза модальною накладкою.
 * Глобальні обробники команд використовують цей реєстр, щоб не виконувати команди за відкритим вікном.</p>
 */
public final class SceneKeyboardModality {

    private static final Object REGISTRY_KEY = SceneKeyboardModality.class;

    /** Забороняє створення екземплярів утиліти зі статичними операціями. */
    private SceneKeyboardModality() {
    }

    /**
     * Реєструє активну межу клавіатурної модальності в указаній сцені.
     * Повторна реєстрація того самого вузла не створює дубліката.
     *
     * @param scene сцена, до якої належить модальна накладка.
     * @param boundary вузол, що реалізує контракт межі гарячих клавіш.
     * @throws NullPointerException якщо сцену або вузол не задано.
     * @throws IllegalArgumentException якщо вузол не реалізує {@link EditorKeyboardShortcutBoundary}.
     */
    public static void register(Scene scene, Node boundary) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(boundary, "boundary");
        if (!(boundary instanceof EditorKeyboardShortcutBoundary)) {
            throw new IllegalArgumentException("Модальна межа має реалізовувати EditorKeyboardShortcutBoundary");
        }
        Registry registry = registryFor(scene, true);
        registry.boundaries.add(boundary);
    }

    /**
     * Знімає реєстрацію межі клавіатурної модальності; повторне зняття безпечне.
     * Якщо активних меж більше немає, прибирає реєстр зі властивостей сцени.
     *
     * @param scene сцена, з якої потрібно прибрати межу; {@code null} дозволено.
     * @param boundary вузол модальної накладки; {@code null} дозволено.
     */
    public static void unregister(Scene scene, Node boundary) {
        if (scene == null || boundary == null) {
            return;
        }
        Registry registry = registryFor(scene, false);
        if (registry == null) {
            return;
        }
        registry.boundaries.remove(boundary);
        if (registry.boundaries.isEmpty()) {
            scene.getProperties().remove(REGISTRY_KEY);
        }
    }

    /**
     * Перевіряє, чи має сцена активну межу модальності для клавіатурної події.
     * Перевірка навмисно враховує всю сцену, щоб блокувати події, спрямовані поза накладкою.
     *
     * @param target ціль події JavaFX.
     * @return {@code true}, якщо в сцені цілі є активна межа, що блокує глобальні команди.
     */
    public static boolean hasActiveBoundaryFor(Object target) {
        if (!(target instanceof Node node) || node.getScene() == null) {
            return false;
        }
        Registry registry = registryFor(node.getScene(), false);
        if (registry == null) {
            return false;
        }
        return registry.boundaries.stream()
                .filter(EditorKeyboardShortcutBoundary.class::isInstance)
                .map(EditorKeyboardShortcutBoundary.class::cast)
                .anyMatch(EditorKeyboardShortcutBoundary::blocksEditorShortcuts);
    }

    /**
     * Отримує наявний реєстр сцени або створює його за потреби.
     *
     * @param scene сцена, для якої потрібен реєстр.
     * @param create {@code true}, якщо реєстр потрібно створити за відсутності.
     * @return реєстр сцени або {@code null}, якщо він відсутній і створення заборонене.
     * @throws IllegalStateException якщо ключ реєстру сцени зайнятий об'єктом іншого типу.
     */
    private static Registry registryFor(Scene scene, boolean create) {
        Object stored = scene.getProperties().get(REGISTRY_KEY);
        if (stored instanceof Registry registry) {
            return registry;
        }
        if (stored != null) {
            throw new IllegalStateException("За ключем реєстру клавіатурної модальності збережено об'єкт іншого типу");
        }
        if (!create) {
            return null;
        }
        Registry registry = new Registry();
        scene.getProperties().put(REGISTRY_KEY, registry);
        return registry;
    }

    /** Утримує активні межі модальності конкретної сцени за ідентичністю вузлів. */
    private static final class Registry {

        private final Set<Node> boundaries = Collections.newSetFromMap(new IdentityHashMap<>());

        /** Створює порожній реєстр для однієї сцени. */
        private Registry() {
        }
    }
}
