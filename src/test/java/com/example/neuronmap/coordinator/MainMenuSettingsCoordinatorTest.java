package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.settings.SettingsEditSession;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє незалежний життєвий цикл налаштувань головного меню та його інтеграцію з локалізацією. */
final class MainMenuSettingsCoordinatorTest {

    /** Перевіряє, що відкат повертає кожен параметр до початкового значення без постійного збереження. */
    @Test
    void rollbackRestoresAllPreviewedValuesWithoutPersistingThem() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        MainMenuSettingsCoordinator coordinator = new MainMenuSettingsCoordinator(localization);
        AtomicReference<String> activeTheme = new AtomicReference<>("light");
        AtomicReference<String> persistedTheme = new AtomicReference<>("light");
        SettingsEditSession.Setting<String> theme = coordinator.register(
                activeTheme::get, activeTheme::set, persistedTheme::set);

        coordinator.preview(theme, "dark");
        coordinator.previewLanguage(SupportedLanguage.UKRAINIAN);

        assertTrue(coordinator.isActive());
        assertTrue(coordinator.hasChanges());
        assertEquals("dark", activeTheme.get());
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());

        coordinator.rollback();

        assertFalse(coordinator.isActive());
        assertFalse(coordinator.hasChanges());
        assertEquals("light", activeTheme.get());
        assertEquals("light", persistedTheme.get());
        assertEquals(SupportedLanguage.ENGLISH, localization.language());
    }

    /** Перевіряє, що підтвердження зберігає змінене значення і завершує сесію. */
    @Test
    void commitPersistsChangedValuesAndClosesSession() {
        MainMenuSettingsCoordinator coordinator = new MainMenuSettingsCoordinator(
                new LocalizationService(Locale.ENGLISH));
        AtomicReference<String> activeTheme = new AtomicReference<>("light");
        AtomicReference<String> persistedTheme = new AtomicReference<>("light");
        SettingsEditSession.Setting<String> theme = coordinator.register(
                activeTheme::get, activeTheme::set, persistedTheme::set);

        coordinator.preview(theme, "dark");
        coordinator.commit();
        coordinator.commit();

        assertEquals("dark", activeTheme.get());
        assertEquals("dark", persistedTheme.get());
        assertFalse(coordinator.isActive());
        assertFalse(coordinator.hasChanges());
    }

    /** Перевіряє, що запуск нового сеансу спочатку прибирає сторонній, непідтверджений перегляд мови. */
    @Test
    void beginIfNeededCapturesConfirmedLanguageAndIsIdempotent() {
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        MainMenuSettingsCoordinator coordinator = new MainMenuSettingsCoordinator(localization);
        localization.previewLanguage(SupportedLanguage.UKRAINIAN);

        coordinator.beginIfNeeded();
        coordinator.beginIfNeeded();

        assertTrue(coordinator.isActive());
        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        coordinator.previewLanguage(SupportedLanguage.RUSSIAN);
        coordinator.rollback();
        assertEquals(SupportedLanguage.ENGLISH, localization.language());
    }

    /** Перевіряє, що набір налаштувань не можна змінювати посеред активного сеансу. */
    @Test
    void registerRejectsChangesDuringActiveSession() {
        MainMenuSettingsCoordinator coordinator = new MainMenuSettingsCoordinator(
                new LocalizationService(Locale.ENGLISH));
        coordinator.beginIfNeeded();

        assertThrows(IllegalStateException.class, () -> coordinator.register(
                () -> "value",
                ignored -> { },
                ignored -> { }
        ));

        coordinator.rollback();
    }
}
