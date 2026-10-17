package com.example.neuronmap.coordinator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Єдиний координатор призупинення незалежних UI-процесів редактора.
 *
 * <p>Компоненти реєструють власні дії паузи та відновлення, зберігаючи свою внутрішню відповідальність.
 * Іменовані причини призупинення дають змогу вкладати модальні стани: процеси відновлюються лише після
 * зняття останньої причини, а повторна команда з тим самим ідентифікатором не змінює стан.</p>
 */
public final class UiProcessCoordinator {

    private final Map<String, RegisteredProcess> processes = new LinkedHashMap<>();
    private final Set<String> suspensionReasons = new LinkedHashSet<>();
    private boolean disposed;

    /** Створює центр керування UI-процесами без зареєстрованих процесів і причин призупинення. */
    public UiProcessCoordinator() {
    }

    /**
     * Реєструє процес і його власні операції призупинення та відновлення.
     * Процес, доданий під час паузи, призупиняється одразу.
     *
     * @param id унікальний непорожній ідентифікатор процесу.
     * @param pause дія призупинення процесу.
     * @param resume дія відновлення процесу після зняття всіх причин паузи.
     * @return реєстрація, закриття якої від'єднує процес від координатора.
     * @throws NullPointerException якщо один з аргументів не задано.
     * @throws IllegalArgumentException якщо ідентифікатор порожній.
     * @throws IllegalStateException якщо координатор уже завершив роботу або ідентифікатор зайнятий.
     */
    public Registration register(String id, Runnable pause, Runnable resume) {
        requireActive();
        Objects.requireNonNull(pause, "pause");
        Objects.requireNonNull(resume, "resume");
        String normalizedId = requireIdentifier(id, "id");
        if (processes.containsKey(normalizedId)) {
            throw new IllegalStateException("UI process is already registered: " + normalizedId);
        }

        RegisteredProcess process = new RegisteredProcess(pause, resume);
        if (isSuspended()) {
            pauseProcess(process);
        }
        processes.put(normalizedId, process);
        return new Registration(this, normalizedId);
    }

    /**
     * Додає причину призупинення й призупиняє всі зареєстровані процеси під час першого переходу до паузи.
     *
     * @param reason унікальний непорожній ідентифікатор причини, наприклад {@code main-menu}.
     * @throws NullPointerException якщо причину не задано.
     * @throws IllegalArgumentException якщо причина порожня.
     * @throws IllegalStateException якщо призупинення не вдалося застосувати або координатор завершив роботу.
     */
    public void suspend(String reason) {
        requireActive();
        String normalizedReason = requireIdentifier(reason, "reason");
        if (!suspensionReasons.add(normalizedReason) || suspensionReasons.size() > 1) {
            return;
        }

        List<RegisteredProcess> pausedProcesses = new ArrayList<>();
        try {
            for (RegisteredProcess process : processes.values()) {
                pauseProcess(process);
                pausedProcesses.add(process);
            }
        } catch (RuntimeException exception) {
            for (int index = pausedProcesses.size() - 1; index >= 0; index--) {
                try {
                    resumeProcess(pausedProcesses.get(index));
                } catch (RuntimeException resumeException) {
                    exception.addSuppressed(resumeException);
                }
            }
            suspensionReasons.remove(normalizedReason);
            throw exception;
        }
    }

    /**
     * Знімає вказану причину призупинення та відновлює процеси у зворотному порядку реєстрації,
     * лише якщо більше не залишилося активних причин.
     *
     * @param reason ідентифікатор причини, яку потрібно зняти.
     * @throws NullPointerException якщо причину не задано.
     * @throws IllegalArgumentException якщо причина порожня.
     * @throws IllegalStateException якщо відновлення не вдалося або координатор завершив роботу.
     */
    public void resume(String reason) {
        requireActive();
        String normalizedReason = requireIdentifier(reason, "reason");
        if (!suspensionReasons.remove(normalizedReason) || isSuspended()) {
            return;
        }

        List<RegisteredProcess> resumedProcesses = new ArrayList<>();
        List<RegisteredProcess> reversed = new ArrayList<>(processes.values());
        try {
            for (int index = reversed.size() - 1; index >= 0; index--) {
                RegisteredProcess process = reversed.get(index);
                if (process.suspendedByCoordinator) {
                    resumeProcess(process);
                    resumedProcesses.add(process);
                }
            }
        } catch (RuntimeException exception) {
            for (int index = resumedProcesses.size() - 1; index >= 0; index--) {
                try {
                    pauseProcess(resumedProcesses.get(index));
                } catch (RuntimeException pauseException) {
                    exception.addSuppressed(pauseException);
                }
            }
            suspensionReasons.add(normalizedReason);
            throw exception;
        }
    }

    /**
     * Повертає, чи залишається активною хоча б одна причина призупинення.
     *
     * @return {@code true}, якщо зареєстровані процеси мають залишатися на паузі.
     */
    public boolean isSuspended() {
        return !suspensionReasons.isEmpty();
    }

    /**
     * Завершує координатор і від'єднує всі процеси без їхнього повторного запуску.
     * Цей режим призначено для завершення редактора, коли його підсистеми вже очищуються окремо.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        suspensionReasons.clear();
        processes.clear();
    }

    /**
     * Від'єднує процес після закриття його реєстрації.
     * Якщо процес був призупинений координатором, перед від'єднанням він відновлюється.
     *
     * @param id ідентифікатор процесу, який потрібно від'єднати.
     */
    private void unregister(String id) {
        if (disposed) {
            return;
        }
        RegisteredProcess process = processes.get(id);
        if (process == null) {
            return;
        }
        if (process.suspendedByCoordinator) {
            resumeProcess(process);
        }
        processes.remove(id);
    }

    /** Виконує призупинення один раз і записує його лише після успішного callback-а. */
    private void pauseProcess(RegisteredProcess process) {
        if (process.suspendedByCoordinator) {
            return;
        }
        process.pause.run();
        process.suspendedByCoordinator = true;
    }

    /** Відновлює лише процес, який координатор справді призупинив. */
    private void resumeProcess(RegisteredProcess process) {
        if (!process.suspendedByCoordinator) {
            return;
        }
        process.resume.run();
        process.suspendedByCoordinator = false;
    }

    /** Перевіряє, що ідентифікатор не є null, порожнім або складеним лише з пробілів. */
    private static String requireIdentifier(String value, String argumentName) {
        Objects.requireNonNull(value, argumentName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(argumentName + " must not be blank");
        }
        return value;
    }

    /** Відхиляє операції, що намагаються змінити вже завершений координатор. */
    private void requireActive() {
        if (disposed) {
            throw new IllegalStateException("UI process coordinator has been disposed");
        }
    }

    /** Зберігає callback-и одного процесу та ознаку паузи, встановленої координатором. */
    private static final class RegisteredProcess {

        private final Runnable pause;
        private final Runnable resume;
        private boolean suspendedByCoordinator;

        /** Створює запис процесу з обов'язковими діями призупинення та відновлення. */
        private RegisteredProcess(Runnable pause, Runnable resume) {
            this.pause = pause;
            this.resume = resume;
        }
    }

    /**
     * Керує реєстрацією одного процесу й забезпечує ідемпотентне від'єднання.
     */
    public static final class Registration implements AutoCloseable {

        private final UiProcessCoordinator owner;
        private final String id;
        private boolean closed;

        /**
         * Створює маркер реєстрації, пов'язаний із координатором-власником.
         *
         * @param owner координатор, якому належить зареєстрований процес.
         * @param id ідентифікатор цього процесу.
         */
        private Registration(UiProcessCoordinator owner, String id) {
            this.owner = owner;
            this.id = id;
        }

        /**
         * Від'єднує процес від координатора; повторне закриття не виконує додаткових дій.
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            owner.unregister(id);
            closed = true;
        }
    }
}
