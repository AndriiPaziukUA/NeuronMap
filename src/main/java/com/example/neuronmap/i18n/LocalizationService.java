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

/** Application-wide localization based on standard Java ResourceBundle files. */
public final class LocalizationService {

    public static final String LANGUAGE_KEY = "language";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final GlobalSettingsStore settings;
    private final List<Consumer<Locale>> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;

    public LocalizationService(GlobalSettingsStore settings) {
        this.settings = settings;
        this.locale = loadInitialLocale(settings);
        this.bundle = loadBundle(locale);
    }

    public LocalizationService(Locale initialLocale) {
        this.settings = null;
        this.locale = SupportedLanguage.fromLocale(initialLocale).locale();
        this.bundle = loadBundle(locale);
    }

    public Locale locale() {
        return locale;
    }

    public SupportedLanguage language() {
        return SupportedLanguage.fromLocale(locale);
    }

    public String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    public String text(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    public String displayName(SupportedLanguage language) {
        if (language == null) {
            return "";
        }
        return text(language.displayKey());
    }

    public List<SupportedLanguage> supportedLanguagesInDisplayOrder() {
        List<SupportedLanguage> languages = new ArrayList<>(
                List.of(SupportedLanguage.values())
        );
        Collator collator = Collator.getInstance(locale);
        languages.sort(Comparator.comparing(this::displayName, collator));
        return List.copyOf(languages);
    }

    public void addListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

    /** Applies and persists the selected language. */
    public void setLanguage(SupportedLanguage language) {
        applyLanguage(language, true);
    }

    /** Applies the selected language only in memory. */
    public void previewLanguage(SupportedLanguage language) {
        applyLanguage(language, false);
    }

    /** Persists the currently applied language. */
    public void persistCurrentLanguage() {
        if (settings != null) {
            settings.save(LANGUAGE_KEY, locale.toLanguageTag());
        }
    }

    private void applyLanguage(SupportedLanguage language, boolean persist) {
        if (language == null) {
            return;
        }
        setLocale(language.locale(), persist);
    }

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

    private static Locale loadInitialLocale(GlobalSettingsStore settings) {
        if (settings == null) {
            return Locale.forLanguageTag("uk");
        }
        String stored = settings.load(LANGUAGE_KEY);
        if (stored == null || stored.isBlank()) {
            return Locale.forLanguageTag("uk");
        }
        try {
            return SupportedLanguage.fromLocale(Locale.forLanguageTag(stored)).locale();
        } catch (RuntimeException exception) {
            return Locale.forLanguageTag("uk");
        }
    }

    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
