package com.example.neuronmap.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Координує початок редагування, підтвердження та скасування кількох незалежних налаштувань.
 * Кожен учасник самостійно зберігає свій початковий стан, а сесія забезпечує спільний життєвий цикл.
 */
public final class SettingsEditSession {

    private final List<Participant> participants = new ArrayList<>();
    private boolean active;

    /** Створює порожню сесію редагування, до якої контролер додає керовані налаштування. */
    public SettingsEditSession() {
    }

    /**
     * Реєструє налаштування, яким потрібні попередній перегляд, підтвердження та відкат.
     * Реєстрація після початку редагування заборонена, щоб склад транзакції не змінювався посеред сеансу.
     *
     * @param participant учасник, який керує життєвим циклом одного налаштування.
     * @throws NullPointerException якщо учасника не задано.
     * @throws IllegalStateException якщо сесія вже активна.
     */
    public void register(Participant participant) {
        if (active) {
            throw new IllegalStateException("Неможливо додати налаштування під час активного редагування");
        }
        Participant checkedParticipant = Objects.requireNonNull(participant, "participant");
        if (!participants.contains(checkedParticipant)) {
            participants.add(checkedParticipant);
        }
    }

    /**
     * Починає редагування всіх зареєстрованих налаштувань.
     * Повторний виклик активної сесії не перезаписує початкові значення.
     */
    public void begin() {
        if (active) {
            return;
        }
        for (Participant participant : List.copyOf(participants)) {
            participant.begin();
        }
        active = true;
    }

    /**
     * Перевіряє, чи має хоча б одне налаштування непідтверджені зміни в активній сесії.
     *
     * @return {@code true}, якщо сесія активна та містить зміни.
     */
    public boolean hasUnsavedChanges() {
        return active && participants.stream().anyMatch(Participant::hasUnsavedChanges);
    }

    /**
     * Підтверджує зміни всіх налаштувань і завершує сесію.
     * Якщо збереження учасника завершується помилкою, сесія лишається активною для коректної обробки помилки.
     */
    public void save() {
        if (!active) {
            return;
        }
        for (Participant participant : List.copyOf(participants)) {
            if (participant.hasUnsavedChanges()) {
                participant.save();
            }
        }
        active = false;
    }

    /**
     * Скасовує зміни у зворотному порядку реєстрації та завершує сесію.
     * Якщо відкат завершується помилкою, активність сесії зберігається, щоб помилку не приховати.
     */
    public void discard() {
        if (!active) {
            return;
        }
        List<Participant> snapshot = List.copyOf(participants);
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            snapshot.get(index).discard();
        }
        active = false;
    }

    /**
     * Описує життєвий цикл одного редагованого налаштування для спільної сесії.
     * Реалізація відповідає за власні значення та має відновлювати не лише дані, а й пов'язаний стан інтерфейсу.
     */
    public interface Participant {

        /** Готує налаштування до редагування та фіксує його початковий стан, якщо це потрібно. */
        void begin();

        /**
         * Перевіряє, чи відрізняється поточне значення від підтвердженого.
         *
         * @return {@code true}, якщо налаштування має непідтверджену зміну.
         */
        boolean hasUnsavedChanges();

        /** Зберігає поточне значення як підтверджене. */
        void save();

        /** Відновлює підтверджене значення та пов'язане з ним відображення. */
        void discard();
    }
}
