package com.example.neuronmap.settings;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє централізований життєвий цикл попереднього перегляду, збереження та скасування налаштувань. */
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

    /** Перевіряє, що rollback повторно застосовує початкові значення всіх налаштувань, відновлюючи пов'язані ефекти. */
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

}
