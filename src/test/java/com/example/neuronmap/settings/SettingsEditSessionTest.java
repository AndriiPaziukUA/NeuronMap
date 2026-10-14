package com.example.neuronmap.settings;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє спільний життєвий цикл попереднього перегляду, збереження та скасування типізованих налаштувань. */
final class SettingsEditSessionTest {

    /** Перевіряє, що скасування відновлює початкові значення кількох налаштувань без запису у сховище. */
    @Test
    void rollbackRestoresEveryPreviewedSettingWithoutPersisting() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> theme = new AtomicReference<>("light");
        AtomicReference<Integer> scale = new AtomicReference<>(100);
        AtomicInteger persistCalls = new AtomicInteger();
        SettingsEditSession.Setting<String> themeSetting = session.register(
                theme::get, theme::set, ignored -> persistCalls.incrementAndGet());
        SettingsEditSession.Setting<Integer> scaleSetting = session.register(
                scale::get, scale::set, ignored -> persistCalls.incrementAndGet());

        session.begin();
        session.preview(themeSetting, "dark");
        session.preview(scaleSetting, 125);

        assertTrue(session.hasChanges());
        assertEquals("dark", theme.get());
        assertEquals(125, scale.get());

        session.rollback();

        assertEquals("light", theme.get());
        assertEquals(100, scale.get());
        assertEquals(0, persistCalls.get());
        assertFalse(session.isActive());
        assertFalse(session.hasChanges());
    }

    /** Перевіряє, що підтвердження зберігає тільки змінені значення та завершує сеанс. */
    @Test
    void commitPersistsChangedValuesAndClosesSession() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> value = new AtomicReference<>("before");
        AtomicReference<String> persisted = new AtomicReference<>("before");
        SettingsEditSession.Setting<String> setting = session.register(
                value::get, value::set, persisted::set);

        session.begin();
        session.preview(setting, "after");
        session.commit();

        assertEquals("after", value.get());
        assertEquals("after", persisted.get());
        assertFalse(session.isActive());
    }

    /** Перевіряє, що повернення прев'ю до початкового значення прибирає ознаку незбережених змін. */
    @Test
    void returningPreviewToOriginalValueClearsDirtyState() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> value = new AtomicReference<>("light");
        AtomicReference<String> persisted = new AtomicReference<>("light");
        SettingsEditSession.Setting<String> setting = session.register(
                value::get, value::set, persisted::set);

        session.begin();
        session.preview(setting, "dark");
        assertTrue(session.hasChanges());

        session.preview(setting, "light");

        assertFalse(session.hasChanges());
        assertEquals("light", persisted.get());
        session.rollback();
    }

    /** Перевіряє, що новий сеанс бере за початкове значення результат попереднього збереження. */
    @Test
    void eachSessionCapturesItsOwnOriginalValue() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> value = new AtomicReference<>("first");
        AtomicReference<String> persisted = new AtomicReference<>("first");
        SettingsEditSession.Setting<String> setting = session.register(
                value::get, value::set, persisted::set);

        session.begin();
        session.preview(setting, "second");
        session.commit();

        session.begin();
        session.preview(setting, "third");
        session.rollback();

        assertEquals("second", value.get());
        assertEquals("second", persisted.get());
    }

    /** Перевіряє, що rollback повторно застосовує початкове значення для відновлення пов'язаних ефектів. */
    @Test
    void rollbackReappliesOriginalValueEvenWhenValueMatchesSnapshot() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> value = new AtomicReference<>("initial");
        AtomicInteger previewCalls = new AtomicInteger();
        SettingsEditSession.Setting<String> setting = session.register(
                value::get,
                candidate -> {
                    previewCalls.incrementAndGet();
                    value.set(candidate);
                },
                ignored -> { });

        session.begin();
        session.rollback();

        assertEquals(1, previewCalls.get());
        assertEquals("initial", value.get());
        assertFalse(session.isActive());
    }

    /** Перевіряє, що помилка одного відкату не заважає відновленню інших параметрів і дозволяє повторити відкат. */
    @Test
    void rollbackContinuesAfterFailureAndKeepsSessionOpenForRetry() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<String> firstValue = new AtomicReference<>("first-original");
        AtomicReference<String> secondValue = new AtomicReference<>("second-original");
        AtomicBoolean failFirstRestore = new AtomicBoolean(true);
        SettingsEditSession.Setting<String> first = session.register(
                firstValue::get,
                firstValue::set,
                ignored -> { });
        SettingsEditSession.Setting<String> second = session.register(
                secondValue::get,
                value -> {
                    if ("second-original".equals(value) && failFirstRestore.getAndSet(false)) {
                        throw new IllegalStateException("Тестова помилка відновлення");
                    }
                    secondValue.set(value);
                },
                ignored -> { });

        session.begin();
        session.preview(first, "first-preview");
        session.preview(second, "second-preview");

        assertThrows(IllegalStateException.class, session::rollback);
        assertEquals("first-original", firstValue.get());
        assertEquals("second-preview", secondValue.get());
        assertTrue(session.isActive());

        session.rollback();

        assertEquals("first-original", firstValue.get());
        assertEquals("second-original", secondValue.get());
        assertFalse(session.isActive());
    }

    /** Перевіряє, що сесія відхиляє дескриптор, зареєстрований в іншому екземплярі. */
    @Test
    void refusesSettingOwnedByAnotherSession() {
        SettingsEditSession firstSession = new SettingsEditSession();
        SettingsEditSession secondSession = new SettingsEditSession();
        AtomicReference<String> value = new AtomicReference<>("initial");
        SettingsEditSession.Setting<String> setting = firstSession.register(
                value::get, value::set, ignored -> { });
        secondSession.begin();

        assertThrows(IllegalStateException.class, () -> secondSession.preview(setting, "preview"));
        secondSession.rollback();
    }
}
