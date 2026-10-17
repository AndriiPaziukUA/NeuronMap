package com.example.neuronmap.view.status;

/** Керує показом короткочасних повідомлень у рядку стану редактора. */
public final class StatusMessagePresenter {

    private final StatusBarView statusBar;
    private final StatusMessageLifecycle lifecycle;
    private boolean disposed;

    /**
     * Створює презентер, прив'язаний до конкретного рядка стану та його області наведення.
     *
     * @param statusBar подання рядка стану, в якому потрібно показувати повідомлення.
     * @throws IllegalArgumentException якщо подання рядка стану не передано.
     */
    public StatusMessagePresenter(StatusBarView statusBar) {
        if (statusBar == null) {
            throw new IllegalArgumentException("statusBar must not be null");
        }
        this.statusBar = statusBar;
        lifecycle = new StatusMessageLifecycle(statusBar.node(), statusBar.messageLabel());
    }

    /** Показує повідомлення й запускає його життєвий цикл, якщо презентер не завершив роботу. */
    public void show(String message) {
        if (disposed) {
            return;
        }
        String normalized = message == null ? "" : message;
        statusBar.setText(normalized);
        if (normalized.isBlank()) {
            lifecycle.clear();
            return;
        }
        lifecycle.show(normalized);
    }

    /** Призупиняє таймер і згасання повідомлення на час модального стану редактора. */
    public void pause() {
        if (!disposed) {
            lifecycle.pause();
        }
    }

    /** Відновлює таймер повідомлення після зняття всіх причин призупинення редактора. */
    public void resume() {
        if (!disposed) {
            lifecycle.resume();
        }
    }

    /** Повертає, чи призупинено таймери повідомлення. */
    public boolean isPaused() {
        return !disposed && lifecycle.isSuspended();
    }

    /** Зупиняє анімації й таймери та від'єднує обробники наведення; повторні виклики безпечні. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        lifecycle.dispose();
    }
}
