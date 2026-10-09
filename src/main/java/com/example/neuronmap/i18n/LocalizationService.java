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

/**
 * Завантажує переклади, визначає поточну мову та повідомляє про її зміну.
 */
public final class LocalizationService {

    public static final String LANGUAGE_KEY = "language";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final GlobalSettingsStore settings;
    private final List<Consumer<Locale>> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;

    /**
     * Повертає результат операції «локалізація служба».
     *
     * @param settings набір налаштувань.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public LocalizationService(GlobalSettingsStore settings) {
        this.settings = settings;
        this.locale = loadInitialLocale(settings);
        this.bundle = loadBundle(locale);
    }

    /**
     * Повертає результат операції «локалізація служба».
     *
     * @param initialLocale значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public LocalizationService(Locale initialLocale) {
        this.settings = null;
        this.locale = SupportedLanguage.fromLocale(initialLocale).locale();
        this.bundle = loadBundle(locale);
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Повертає результат операції «мова».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SupportedLanguage language() {
        return SupportedLanguage.fromLocale(locale);
    }

    /**
     * Повертає результат операції «текст».
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    /**
     * Повертає результат операції «текст».
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @param arguments значення, що визначає відповідну операцію для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String text(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    /**
     * Відображає «відповідну операцію» в інтерфейсі.
     *
     * @param language значення, що визначає мова для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String displayName(SupportedLanguage language) {
        if (language == null) {
            return "";
        }
        return text(language.displayKey());
    }

    /**
     * Повертає результат операції «відображати».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<SupportedLanguage> supportedLanguagesInDisplayOrder() {
        List<SupportedLanguage> languages = new ArrayList<>(
                List.of(SupportedLanguage.values())
        );
        Collator collator = Collator.getInstance(locale);
        languages.sort(Comparator.comparing(this::displayName, collator));
        return List.copyOf(languages);
    }

    /**
     * Виконує операцію «додати слухач».
     *
     * @param listener слухач, якого потрібно сповістити.
     */
    public void addListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Видаляє або скидає дані, повʼязані з «слухач».
     *
     * @param listener слухач, якого потрібно сповістити.
     */
    public void removeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

/**
 * Задає або оновлює значення, повʼязані з «мова».
 *
 * @param language значення, що визначає мова для цієї операції.
 */
public void setLanguage(SupportedLanguage language) {
        applyLanguage(language, true);
    }

/**
 * Виконує операцію «попередній перегляд мова».
 *
 * @param language значення, що визначає мова для цієї операції.
 */
public void previewLanguage(SupportedLanguage language) {
        applyLanguage(language, false);
    }

/**
 * Зберігає дані, повʼязані з «поточний мова», у відповідному сховищі.
 */
public void persistCurrentLanguage() {
        if (settings != null) {
            settings.save(LANGUAGE_KEY, locale.toLanguageTag());
        }
    }

    /**
     * Обробляє «мова».
     *
     * @param language значення, що визначає мова для цієї операції.
     *
     * @param persist значення, що визначає зберігати для цієї операції.
     */
    private void applyLanguage(SupportedLanguage language, boolean persist) {
        if (language == null) {
            return;
        }
        setLocale(language.locale(), persist);
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param newLocale значення, що визначає новий для цієї операції.
     *
     * @param persist значення, що визначає зберігати для цієї операції.
     */
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

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param settings набір налаштувань.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param locale значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
