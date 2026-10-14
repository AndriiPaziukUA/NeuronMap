package com.example.neuronmap.app;

import java.util.Objects;

/**
 * Узгоджує одноразове збереження стану головного вікна та завершення редактора.
 *
 * <p>Компонент не залежить від JavaFX і може використовуватися як для звичайного закриття вікна,
 * так і для резервного завершення застосунку під час зупинки JavaFX.</p>
 */
public final class WindowLifecycleCoordinator {

    private final Runnable persistWindowState;
    private final Runnable shutdownEditor;
    private boolean shutdown;

    /**
     * Створює координатор фінального збереження стану вікна та звільнення ресурсів редактора.
     *
     * @param persistWindowState дія збереження геометрії вікна.
     * @param shutdownEditor дія завершення редактора та звільнення його ресурсів.
     * @throws NullPointerException якщо будь-яку з дій не задано.
     */
    public WindowLifecycleCoordinator(Runnable persistWindowState, Runnable shutdownEditor) {
        this.persistWindowState = Objects.requireNonNull(persistWindowState, "persistWindowState");
        this.shutdownEditor = Objects.requireNonNull(shutdownEditor, "shutdownEditor");
    }

    /**
     * Один раз зберігає геометрію вікна, а потім завершує редактор навіть у разі помилки збереження.
     * Повторні запити не запускають очищення вдруге.
     *
     * @throws RuntimeException якщо одна з переданих дій завершується неперехопленою помилкою.
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }

        shutdown = true;
        Throwable failure = null;
        try {
            persistWindowState.run();
        } catch (RuntimeException | Error exception) {
            failure = exception;
        }

        try {
            shutdownEditor.run();
        } catch (RuntimeException | Error exception) {
            if (failure == null) {
                failure = exception;
            } else if (failure != exception) {
                failure.addSuppressed(exception);
            }
        }

        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
