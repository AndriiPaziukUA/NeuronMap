package com.example.neuronmap.view.status;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.LocalizationService.LocalizedMessage;

import java.util.Locale;
import java.util.function.Consumer;

/** Керує показом короткочасних повідомлень у рядку стану редактора та оновленням їхніх перекладів. */
public final class StatusMessagePresenter {

    private final StatusBarView statusBar;
    private final StatusMessageLifecycle lifecycle;
    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener;
    private LocalizedMessage displayedMessage;
    private boolean disposed;

    /**
     * Створює презентер рядка стану без автоматичного оновлення довільного тексту при зміні мови.
     *
     * @param statusBar подання рядка стану, в якому потрібно показувати повідомлення.
     * @throws IllegalArgumentException якщо подання рядка стану не передано.
     */
    public StatusMessagePresenter(StatusBarView statusBar) {
        this(statusBar, null);
    }

    /**
     * Створює презентер, який повторно локалізує активне повідомлення без перезапуску його таймера.
     *
     * @param statusBar подання рядка стану, в якому потрібно показувати повідомлення.
     * @param localization спільна служба локалізації; {@code null} вимикає реакцію на зміну мови.
     * @throws IllegalArgumentException якщо подання рядка стану не передано.
     */
    public StatusMessagePresenter(StatusBarView statusBar, LocalizationService localization) {
        if (statusBar == null) {
            throw new IllegalArgumentException("statusBar must not be null");
        }
        this.statusBar = statusBar;
        this.localization = localization;
        lifecycle = new StatusMessageLifecycle(statusBar.node(), statusBar.messageLabel());
        localizationListener = ignored -> refreshDisplayedMessage();
        if (localization != null) {
            localization.addListener(localizationListener);
        }
    }

    /**
     * Показує повідомлення й запускає його життєвий цикл, якщо презентер не завершив роботу.
     * Якщо рядок отримано від служби локалізації безпосередньо перед викликом, його ключ зберігається
     * для миттєвого оновлення тексту під час попереднього перегляду або відкату мови.
     *
     * @param message текст повідомлення; порожній текст приховує мітку.
     */
    public void show(String message) {
        if (disposed) {
            return;
        }
        LocalizedMessage localizedMessage = localization == null
                ? null
                : localization.consumeLocalizedMessage(message);
        String normalized = message == null ? "" : message;
        displayedMessage = normalized.isBlank() ? null : localizedMessage;
        statusBar.setText(normalized);
        if (normalized.isBlank()) {
            lifecycle.clear();
            return;
        }
        lifecycle.show(normalized);
    }

    /** Оновлює лише текст активного локалізованого повідомлення, не змінюючи його таймер або стан анімації. */
    private void refreshDisplayedMessage() {
        if (disposed || localization == null || displayedMessage == null) {
            return;
        }
        String translatedText = localization.text(displayedMessage.key(), displayedMessage.arguments());
        localization.consumeLocalizedMessage(translatedText);
        statusBar.setText(translatedText);
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

    /** Зупиняє анімації, від'єднує слухача локалізації та обробники наведення; повторні виклики безпечні. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (localization != null) {
            localization.removeListener(localizationListener);
        }
        displayedMessage = null;
        lifecycle.dispose();
    }
}
