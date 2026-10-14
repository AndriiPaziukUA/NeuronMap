package com.example.neuronmap.view.status;

/**
 * Керує показом короткочасних повідомлень безпосередньо в рядку стану редактора.
 *
 * <p>Презентер отримує конкретне подання рядка стану й не шукає графічні вузли
 * у сцені, тому сторонні мітки не можуть випадково отримати його обробники подій.</p>
 */
public final class StatusMessagePresenter {

    private final StatusBarView statusBar;
    private final StatusMessageLifecycle lifecycle;
    private boolean disposed;

    /**
     * Створює презентер, прив’язаний до конкретного рядка стану та його області наведення.
     *
     * @param statusBar подання рядка стану, в якому потрібно показувати повідомлення.
     * @throws IllegalArgumentException якщо подання рядка стану не передано.
     */
    public StatusMessagePresenter(StatusBarView statusBar) {
        if (statusBar == null) {
            throw new IllegalArgumentException("statusBar must not be null");
        }

        this.statusBar = statusBar;
        this.lifecycle = new StatusMessageLifecycle(
                statusBar.node(),
                statusBar.messageLabel()
        );
    }

    /**
     * Показує повідомлення в рядку стану та запускає його життєвий цикл.
     *
     * @param message текст повідомлення; {@code null} трактується як порожній текст.
     */
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

    /**
     * Зупиняє анімації й таймери та від’єднує обробники наведення.
     * Повторні виклики безпечні.
     */
    public void dispose() {
        if (disposed) {
            return;
        }

        disposed = true;
        lifecycle.dispose();
    }
}
