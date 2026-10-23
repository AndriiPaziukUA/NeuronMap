package com.example.neuronmap.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Керує сеансом редагування налаштувань: зберігає початкові значення, застосовує попередній перегляд,
 * підтверджує зміни або відновлює всі значення у разі скасування.
 *
 * <p>Кожне налаштування реєструється один раз із функціями читання, тимчасового застосування
 * та постійного збереження. Це дозволяє додавати нові налаштування без окремої логіки відкочування
 * для кожного елемента інтерфейсу.</p>
 */
public final class SettingsEditSession {

    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean active;

    /**
     * Реєструє налаштування, яким керуватиме цей сеанс.
     *
     * @param currentValue постачальник фактично активного значення.
     * @param previewAction дія тимчасового застосування значення без постійного збереження.
     * @param persistAction дія постійного збереження поточного значення.
     * @param <T> тип значення налаштування.
     * @return дескриптор, який використовується для попереднього перегляду цього налаштування.
     * @throws IllegalStateException якщо сеанс уже відкритий.
     * @throws NullPointerException якщо будь-який callback не задано.
     */
    public <T> Setting<T> register(
            Supplier<T> currentValue,
            Consumer<T> previewAction,
            Consumer<T> persistAction
    ) {
        if (active) {
            throw new IllegalStateException("Cannot register a setting while an edit session is active");
        }
        Setting<T> setting = new Setting<>(currentValue, previewAction, persistAction);
        settings.add(setting);
        return setting;
    }

    /** Починає сеанс і знімає початковий знімок усіх зареєстрованих налаштувань. */
    public void begin() {
        if (active) {
            throw new IllegalStateException("Settings edit session is already active");
        }
        for (Setting<?> setting : settings) {
            setting.capture();
        }
        active = true;
    }

    /**
     * Тимчасово застосовує значення зареєстрованого налаштування.
     *
     * @param setting дескриптор налаштування.
     * @param value значення для попереднього перегляду.
     * @param <T> тип значення налаштування.
     * @throws IllegalStateException якщо сеанс не відкритий або дескриптор не належить цьому сеансу.
     */
    public <T> void preview(Setting<T> setting, T value) {
        requireActiveSetting(setting);
        setting.preview(value);
    }

    /**
     * Оновлює початковий знімок одного налаштування після того, як його нове значення було збережено ззовні.
     * Інші налаштування залишаються незмінними в межах сеансу й продовжують брати участь у визначенні
     * незбережених змін. Метод не виконує збереження повторно та не запускає дію попереднього перегляду.
     *
     * @param setting дескриптор параметра, який уже був застосований і збережений іншим власником.
     * @param <T> тип значення налаштування.
     * @throws IllegalStateException якщо сеанс активний, але дескриптор не належить цьому сеансу.
     */
    public <T> void acceptCurrentValue(Setting<T> setting) {
        if (!active) {
            return;
        }
        requireActiveSetting(setting);
        setting.acceptCurrentValue();
    }

    /**
     * Перевіряє, чи відрізняється поточний стан хоча б одного налаштування від знімка сеансу.
     *
     * @return {@code true}, якщо є незбережені зміни.
     */
    public boolean hasChanges() {
        return active && settings.stream().anyMatch(Setting::hasChanged);
    }

    /**
     * Постійно зберігає змінені налаштування та завершує сеанс.
     * Якщо callback збереження викидає виняток, сеанс залишається активним, щоб зміни можна було
     * повторно зберегти або скасувати.
     */
    public void commit() {
        if (!active) {
            return;
        }
        for (Setting<?> setting : settings) {
            if (setting.hasChanged()) {
                setting.persistCurrentValue();
            }
        }
        closeSession();
    }

    /**
     * Відновлює початкові значення у зворотному порядку реєстрації та завершує сеанс.
     * Постійні сховища під час скасування не змінюються.
     */
    public void rollback() {
        if (!active) {
            return;
        }

        RuntimeException failure = null;
        for (int index = settings.size() - 1; index >= 0; index--) {
            try {
                settings.get(index).restoreOriginalValue();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        closeSession();
    }

    /**
     * Повертає, чи відкрито сеанс редагування.
     *
     * @return {@code true}, якщо сеанс активний.
     */
    public boolean isActive() {
        return active;
    }

    /** Перевіряє, що сеанс відкритий, а дескриптор належить цьому екземпляру сеансу. */
    private void requireActiveSetting(Setting<?> setting) {
        if (!active) {
            throw new IllegalStateException("Settings edit session is not active");
        }
        if (setting == null || !settings.contains(setting)) {
            throw new IllegalStateException("Setting is not registered in this edit session");
        }
    }

    /** Завершує сеанс і звільняє знімки початкових значень. */
    private void closeSession() {
        active = false;
        settings.forEach(Setting::clearSnapshot);
    }

    /** Дескриптор одного налаштування, зареєстрованого в {@link SettingsEditSession}. */
    public static final class Setting<T> {

        private final Supplier<T> currentValue;
        private final Consumer<T> previewAction;
        private final Consumer<T> persistAction;
        private T originalValue;
        private boolean captured;

        /** Створює дескриптор без зміни значення до початку сеансу. */
        private Setting(
                Supplier<T> currentValue,
                Consumer<T> previewAction,
                Consumer<T> persistAction
        ) {
            this.currentValue = Objects.requireNonNull(currentValue, "currentValue");
            this.previewAction = Objects.requireNonNull(previewAction, "previewAction");
            this.persistAction = Objects.requireNonNull(persistAction, "persistAction");
        }

        /** Запам'ятовує значення, яке потрібно відновити в разі скасування. */
        private void capture() {
            originalValue = currentValue.get();
            captured = true;
        }

        /** Застосовує передане значення без виклику постійного збереження. */
        private void preview(T value) {
            previewAction.accept(value);
        }

        /** Приймає фактичне вже збережене значення як нову точку відліку поточного сеансу. */
        private void acceptCurrentValue() {
            originalValue = currentValue.get();
        }

        /** Порівнює поточне значення з початковим знімком. */
        private boolean hasChanged() {
            return captured && !Objects.equals(originalValue, currentValue.get());
        }

        /** Передає поточне значення callback-у постійного збереження. */
        private void persistCurrentValue() {
            persistAction.accept(currentValue.get());
        }

        /** Повторно застосовує початкове значення, відновлюючи також пов'язані тимчасові ефекти подання. */
        private void restoreOriginalValue() {
            if (captured) {
                previewAction.accept(originalValue);
            }
        }

        /** Очищує знімок після успішного завершення сеансу. */
        private void clearSnapshot() {
            originalValue = null;
            captured = false;
        }
    }
}
