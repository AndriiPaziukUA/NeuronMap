package com.example.neuronmap.controller;

import javafx.event.Event;
import javafx.event.EventType;

/** Повідомляє компоненти інтерфейсу про новий стан режиму після виконання клавіатурної команди F11. */
public final class FullScreenShortcutEvent extends Event {

    /** Тип події, що сигналізує про зміну повноекранного режиму клавіатурною командою. */
    public static final EventType<FullScreenShortcutEvent> FULL_SCREEN_CHANGED =
            new EventType<>(Event.ANY, "FULL_SCREEN_SHORTCUT_CHANGED");

    private final boolean enabled;

    /**
     * Створює сповіщення про новий збережений стан повноекранного режиму.
     *
     * @param enabled {@code true}, якщо повноекранний режим увімкнено.
     */
    public FullScreenShortcutEvent(boolean enabled) {
        super(FULL_SCREEN_CHANGED);
        this.enabled = enabled;
    }

    /** Повертає стан повноекранного режиму після оброблення клавіатурної команди. */
    public boolean isEnabled() {
        return enabled;
    }
}
