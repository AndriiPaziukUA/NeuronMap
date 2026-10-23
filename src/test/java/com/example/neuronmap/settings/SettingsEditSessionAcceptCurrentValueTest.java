package com.example.neuronmap.settings;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє прийняття зовні збереженого значення без підтвердження інших змін сеансу. */
final class SettingsEditSessionAcceptCurrentValueTest {

    /** Переконується, що оновлення знімка одного параметра не зберігає й не приховує інший параметр. */
    @Test
    void acceptsOnePersistedValueWithoutCommittingOtherChanges() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<Boolean> activeFullScreen = new AtomicReference<>(false);
        AtomicReference<String> activeTheme = new AtomicReference<>("light");
        AtomicInteger fullScreenPersistCalls = new AtomicInteger();
        AtomicInteger themePersistCalls = new AtomicInteger();

        SettingsEditSession.Setting<Boolean> fullScreen = session.register(
                activeFullScreen::get, activeFullScreen::set, ignored -> fullScreenPersistCalls.incrementAndGet());
        session.register(activeTheme::get, activeTheme::set, ignored -> themePersistCalls.incrementAndGet());
        session.begin();

        activeFullScreen.set(true);
        activeTheme.set("dark");
        session.acceptCurrentValue(fullScreen);

        assertTrue(session.hasChanges(), "Зміна теми має залишатися незбереженою");
        session.rollback();

        assertTrue(activeFullScreen.get(), "Зовні збережене значення повноекранного режиму не відкочується");
        assertEquals("light", activeTheme.get(), "Незбережене інше налаштування має відкотитися");
        assertEquals(0, fullScreenPersistCalls.get(), "Прийняття значення не викликає повторного збереження");
        assertEquals(0, themePersistCalls.get(), "Відкат не повинен зберігати інші параметри");
        assertFalse(session.isActive());
    }

    /** Перевіряє, що синхронізація неактивного сеансу не записує значення та не створює активний сеанс. */
    @Test
    void acceptingValueWithoutActiveSessionIsANoOp() {
        SettingsEditSession session = new SettingsEditSession();
        AtomicReference<Boolean> activeValue = new AtomicReference<>(false);
        AtomicInteger persistCalls = new AtomicInteger();
        SettingsEditSession.Setting<Boolean> setting = session.register(
                activeValue::get, activeValue::set, ignored -> persistCalls.incrementAndGet());

        activeValue.set(true);
        session.acceptCurrentValue(setting);

        assertFalse(session.isActive());
        assertFalse(session.hasChanges());
        assertEquals(0, persistCalls.get());
        assertTrue(activeValue.get());
    }
}
