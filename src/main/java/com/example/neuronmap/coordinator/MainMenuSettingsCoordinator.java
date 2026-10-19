package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.settings.SettingsEditSession;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Оркеструє життєвий цикл попереднього перегляду, підтвердження та відкату налаштувань головного меню. */
public final class MainMenuSettingsCoordinator {

    private final LocalizationService localization;
    private final SettingsEditSession editSession = new SettingsEditSession();
    private final SettingsEditSession.Setting<SupportedLanguage> languageSetting;

    /** Створює координатор і реєструє мову як звичайне типізоване налаштування спільної сесії. */
    public MainMenuSettingsCoordinator(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        languageSetting = editSession.register(
                localization::language,
                localization::previewLanguage,
                ignored -> localization.persistCurrentLanguage());
    }

    /** Реєструє налаштування до початку сесії, зберігаючи спільний механізм знімків і відкату. */
    public <T> SettingsEditSession.Setting<T> register(
            Supplier<T> currentValue,
            Consumer<T> previewAction,
            Consumer<T> persistAction
    ) {
        return editSession.register(currentValue, previewAction, persistAction);
    }

    /** Починає сесію за потреби та застосовує тимчасове значення налаштування. */
    public <T> void preview(SettingsEditSession.Setting<T> setting, T value) {
        beginIfNeeded();
        editSession.preview(setting, value);
    }

    /** Застосовує вибрану мову як попередній перегляд, не зберігаючи її в постійному сховищі. */
    public void previewLanguage(SupportedLanguage language) {
        if (language != null) {
            preview(languageSetting, language);
        }
    }

    /** Починає сеанс один раз після синхронізації локалі з останнім підтвердженим вибором. */
    public void beginIfNeeded() {
        if (editSession.isActive()) {
            return;
        }
        localization.discardLanguagePreview();
        editSession.begin();
    }

    /** Підтверджує всі змінені параметри активного сеансу та завершує його. */
    public void commit() {
        editSession.commit();
    }

    /** Відновлює початкові значення й завершує сеанс; без активного сеансу нічого не робить. */
    public void rollback() {
        editSession.rollback();
    }

    /** Перевіряє, чи відрізняється хоча б одне налаштування від початкового значення сеансу. */
    public boolean hasChanges() {
        return editSession.hasChanges();
    }

    /** Повертає, чи триває сеанс редагування налаштувань. */
    public boolean isActive() {
        return editSession.isActive();
    }
}
