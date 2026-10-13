package com.example.neuronmap.controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє спільний життєвий цикл кількох налаштувань в одній сесії редагування. */
final class SettingsEditSessionLifecycleTest {

    /** Перевіряє, що скасування відкочує всі учасники у зворотному порядку реєстрації. */
    @Test
    void discardingSessionRollsBackParticipantsInReverseOrder() {
        SettingsEditSession session = new SettingsEditSession();
        List<String> events = new ArrayList<>();
        TestParticipant first = new TestParticipant("first", events);
        TestParticipant second = new TestParticipant("second", events);
        session.register(first);
        session.register(second);

        session.begin();
        first.changeTo("preview");
        second.changeTo("preview");
        assertTrue(session.hasUnsavedChanges());

        session.discard();

        assertEquals("original", first.value());
        assertEquals("original", second.value());
        assertEquals(List.of("begin:first", "begin:second", "discard:second", "discard:first"), events);
        assertFalse(session.hasUnsavedChanges());
    }

    /** Перевіряє, що збереження викликається лише для учасників зі змінами й закриває сесію. */
    @Test
    void savingSessionConfirmsChangedParticipantsOnly() {
        SettingsEditSession session = new SettingsEditSession();
        List<String> events = new ArrayList<>();
        TestParticipant changed = new TestParticipant("changed", events);
        TestParticipant unchanged = new TestParticipant("unchanged", events);
        session.register(changed);
        session.register(unchanged);

        session.begin();
        changed.changeTo("preview");
        session.save();

        assertEquals("preview", changed.value());
        assertEquals("original", unchanged.value());
        assertEquals(List.of("begin:changed", "begin:unchanged", "save:changed"), events);
        assertFalse(session.hasUnsavedChanges());
    }

    /** Перевіряє, що набір налаштувань не можна змінювати посеред активного редагування. */
    @Test
    void refusesParticipantRegistrationDuringActiveSession() {
        SettingsEditSession session = new SettingsEditSession();
        session.register(new TestParticipant("existing", new ArrayList<>()));
        session.begin();

        assertThrows(IllegalStateException.class,
                () -> session.register(new TestParticipant("late", new ArrayList<>())));
    }

    /** Простий учасник тестує життєвий цикл сесії без JavaFX і файлових залежностей. */
    private static final class TestParticipant implements SettingsEditSession.Participant {

        private final String name;
        private final List<String> events;
        private String value = "original";
        private String originalValue = value;

        /** Створює учасника з незалежним початковим значенням і журналом подій. */
        private TestParticipant(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        /** Замінює поточне значення, імітуючи попередній перегляд налаштування. */
        private void changeTo(String newValue) {
            value = newValue;
        }

        /** Повертає поточне значення для перевірки поведінки сесії. */
        private String value() {
            return value;
        }

        /** Запам'ятовує початкове значення перед редагуванням. */
        @Override
        public void begin() {
            originalValue = value;
            events.add("begin:" + name);
        }

        /** Перевіряє, чи змінилося значення після початку сесії. */
        @Override
        public boolean hasUnsavedChanges() {
            return !originalValue.equals(value);
        }

        /** Фіксує поточне значення як нову базу для наступного редагування. */
        @Override
        public void save() {
            originalValue = value;
            events.add("save:" + name);
        }

        /** Відновлює збережене перед початком сесії значення. */
        @Override
        public void discard() {
            value = originalValue;
            events.add("discard:" + name);
        }
    }
}
