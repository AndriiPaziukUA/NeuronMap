package com.example.neuronmap.controller;

import javafx.scene.Scene;

import java.util.function.Consumer;

/**
 * Адаптер сумісності для коду, який раніше використовував подання повідомлень із пакета controller.
 *
 * @deprecated Подання повідомлень і керування його життєвим циклом розміщені у {@link com.example.neuronmap.view.StatusMessagePresenter}.
 */
@Deprecated(since = "0.7.1", forRemoval = false)
public final class StatusMessagePresenter {

    private final com.example.neuronmap.view.StatusMessagePresenter delegate;

    /**
     * Створює адаптер для подання повідомлень.
     *
     * @param fallback обробник звичайного рядка стану.
     */
    public StatusMessagePresenter(Consumer<String> fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("fallback must not be null");
        }
        this.delegate = new com.example.neuronmap.view.StatusMessagePresenter(fallback);
    }

    /**
     * Передає поданню сцену, у якій потрібно показувати повідомлення.
     *
     * @param scene сцена JavaFX.
     */
    public void attach(Scene scene) {
        delegate.attach(scene);
    }

    /**
     * Передає текст повідомлення до подання.
     *
     * @param message текст повідомлення.
     */
    public void show(String message) {
        delegate.show(message);
    }

    /**
     * Передає поданню команду звільнити ресурси.
     */
    public void dispose() {
        delegate.dispose();
    }
}
