package com.example.neuronmap.i18n;

import com.example.neuronmap.persistence.GlobalSettingsStore;

import java.text.Collator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/** Provides localized UI text, manages the active language, and persists the user's language choice. */
public final class LocalizationService {

    public static final String LANGUAGE_KEY = "language";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final GlobalSettingsStore settings;
    private final List<Consumer<Locale>> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;

    /**
     * Creates the localization service from a persistent global-settings store.
     * A missing or invalid preference defaults to English.
     *
     * @param settings global application settings store; may be null for an in-memory service.
     */
    public LocalizationService(GlobalSettingsStore settings) {
        this.settings = settings;
        this.locale = loadInitialLocale(settings);
        this.bundle = loadBundle(locale);
    }

    /**
     * Creates an in-memory localization service using the supplied initial locale.
     *
     * @param initialLocale locale to use initially; unsupported or null values fall back to English.
     */
    public LocalizationService(Locale initialLocale) {
        this.settings = null;
        this.locale = SupportedLanguage.fromLocale(initialLocale).locale();
        this.bundle = loadBundle(locale);
    }

    /**
     * Returns the current UI locale.
     *
     * @return current normalized locale.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns the currently active supported language.
     *
     * @return the language corresponding to the active locale.
     */
    public SupportedLanguage language() {
        return SupportedLanguage.fromLocale(locale);
    }

    /**
     * Resolves a localized message by key, returning the key itself when no translation exists.
     *
     * @param key resource-bundle message key.
     * @return translated text, or the key when the translation is missing or the key is blank.
     */
    public String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    /**
     * Resolves a localized message and formats it with the supplied arguments.
     *
     * @param key resource-bundle message key.
     * @param arguments values substituted into the message template.
     * @return formatted localized text.
     */
    public String text(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    /**
     * Returns a translated display name for a supported language.
     *
     * @param language language whose display name should be resolved.
     * @return translated language name, or an empty string for null.
     */
    public String displayName(SupportedLanguage language) {
        return language == null ? "" : text(language.displayKey());
    }

    /**
     * Returns supported languages sorted according to their localized display names.
     *
     * @return immutable list of languages in display order.
     */
    public List<SupportedLanguage> supportedLanguagesInDisplayOrder() {
        List<SupportedLanguage> languages = new ArrayList<>(List.of(SupportedLanguage.values()));
        Collator collator = Collator.getInstance(locale);
        languages.sort(Comparator.comparing(this::displayName, collator));
        return List.copyOf(languages);
    }

    /** Registers a listener to receive notifications when the active locale changes. */
    public void addListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Removes a previously registered locale-change listener. */
    public void removeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

    /** Applies a supported language and persists it to global settings when a store is available. */
    public void setLanguage(SupportedLanguage language) {
        applyLanguage(language, true);
    }

    /** Previews a supported language without persisting it, so the user can still discard the change. */
    public void previewLanguage(SupportedLanguage language) {
        applyLanguage(language, false);
    }

    /** Persists the currently active language without changing the displayed locale. */
    public void persistCurrentLanguage() {
        if (settings != null) {
            settings.save(LANGUAGE_KEY, locale.toLanguageTag());
        }
    }

    /** Applies a language if it is non-null and optionally persists it. */
    private void applyLanguage(SupportedLanguage language, boolean persist) {
        if (language != null) {
            setLocale(language.locale(), persist);
        }
    }

    /** Normalizes and applies a locale, notifies listeners, and optionally saves the selection. */
    private void setLocale(Locale newLocale, boolean persist) {
        SupportedLanguage supported = SupportedLanguage.fromLocale(newLocale);
        Locale normalized = supported.locale();

        if (normalized.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
            if (persist) {
                persistCurrentLanguage();
            }
            return;
        }

        locale = normalized;
        bundle = loadBundle(locale);
        if (persist) {
            persistCurrentLanguage();
        }
        for (Consumer<Locale> listener : List.copyOf(listeners)) {
            listener.accept(locale);
        }
    }

    /** Loads a stored language preference or falls back to English when none is available. */
    private static Locale loadInitialLocale(GlobalSettingsStore settings) {
        if (settings == null) {
            return SupportedLanguage.ENGLISH.locale();
        }
        String stored = settings.load(LANGUAGE_KEY);
        if (stored == null || stored.isBlank()) {
            return SupportedLanguage.ENGLISH.locale();
        }
        try {
            return SupportedLanguage.fromLocale(Locale.forLanguageTag(stored)).locale();
        } catch (RuntimeException exception) {
            return SupportedLanguage.ENGLISH.locale();
        }
    }

    /** Loads the resource bundle associated with a supported locale. */
    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
