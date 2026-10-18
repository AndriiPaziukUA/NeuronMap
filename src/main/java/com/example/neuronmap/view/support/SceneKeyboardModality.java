package com.example.neuronmap.view.support;

import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks active modal keyboard boundaries for a scene, including events targeted outside the modal node.
 *
 * <p>Node ancestry alone cannot identify an event targeted at the editor background while a modal overlay is
 * active. Global scene-level shortcuts can consult this registry before handling such an event.</p>
 */
public final class SceneKeyboardModality {

    private static final Object REGISTRY_KEY = SceneKeyboardModality.class;

    private SceneKeyboardModality() {
    }

    /** Registers a visible modal boundary with its owning scene. */
    public static void register(Scene scene, Node boundary) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(boundary, "boundary");
        if (!(boundary instanceof EditorKeyboardShortcutBoundary)) {
            throw new IllegalArgumentException("Modal boundary must implement EditorKeyboardShortcutBoundary");
        }
        Registry registry = registryFor(scene, true);
        registry.boundaries.add(boundary);
    }

    /** Removes a modal boundary from its scene; repeated removal is safe. */
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
     * Returns whether a scene has an active modal boundary for an event targeted at {@code target}.
     * This is intentionally scene-scoped rather than ancestry-scoped: events outside the modal must also be
     * prevented from triggering application-wide shortcuts.
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

    private static Registry registryFor(Scene scene, boolean create) {
        Object stored = scene.getProperties().get(REGISTRY_KEY);
        if (stored instanceof Registry registry) {
            return registry;
        }
        if (stored != null) {
            throw new IllegalStateException("Unexpected value stored for scene keyboard modality");
        }
        if (!create) {
            return null;
        }
        Registry registry = new Registry();
        scene.getProperties().put(REGISTRY_KEY, registry);
        return registry;
    }

    private static final class Registry {
        private final Set<Node> boundaries = Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
