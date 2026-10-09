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
 * Надає переклади інтерфейсу, керує поточною мовою, повідомляє слухачів про її зміну та зберігає вибір користувача.
 */
public final class LocalizationService {

    public static final String LANGUAGE_KEY = "language";
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final GlobalSettingsStore settings;
    private final List<Consumer<Locale>> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;

    /**
     * Створює екземпляр LocalizationService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param settings сховище глобальних налаштувань застосунку.
     */
    public LocalizationService(GlobalSettingsStore settings) {
        this.settings = settings;
        this.locale = loadInitialLocale(settings);
        this.bundle = loadBundle(locale);
    }

    /**
     * Створює екземпляр LocalizationService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param initialLocale локаль, яку потрібно застосувати під час створення служби локалізації.
     */
    public LocalizationService(Locale initialLocale) {
        this.settings = null;
        this.locale = SupportedLanguage.fromLocale(initialLocale).locale();
        this.bundle = loadBundle(locale);
    }

    /**
     * Повертає поточну локаль інтерфейсу.
     *
     * @return поточну локаль інтерфейсу.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Повертає поточну мову інтерфейсу.
     *
     * @return поточну мову інтерфейсу.
     */
    public SupportedLanguage language() {
        return SupportedLanguage.fromLocale(locale);
    }

    /**
     * Повертає переклад заданого ключа для поточної мови; варіант з аргументами підставляє їх у шаблон повідомлення.
     *
     * @param key ключ налаштування або перекладу.
     *
     * @return переклад заданого ключа для поточної мови; варіант з аргументами підставляє їх у шаблон повідомлення.
     */
    public String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    /**
     * Повертає переклад заданого ключа для поточної мови; варіант з аргументами підставляє їх у шаблон повідомлення.
     *
     * @param key ключ налаштування або перекладу.
     * @param arguments аргументи для підстановки в шаблон перекладу.
     *
     * @return переклад заданого ключа для поточної мови; варіант з аргументами підставляє їх у шаблон повідомлення.
     */
    public String text(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    /**
     * Повертає назву мови для поточної локалі інтерфейсу.
     *
     * @param language мова інтерфейсу, яку потрібно застосувати або описати.
     *
     * @return назву мови для поточної локалі інтерфейсу.
     */
    public String displayName(SupportedLanguage language) {
        if (language == null) {
            return "";
        }
        return text(language.displayKey());
    }

    /**
     * Повертає підтримувані мови, відсортовані за локалізованими назвами.
     *
     * @return підтримувані мови, відсортовані за локалізованими назвами.
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
     * Реєструє слухача, якому надсилатиметься нова локаль після зміни мови.
     *
     * @param listener слухач змін локалі, якого потрібно зареєструвати або видалити.
     */
    public void addListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Від’єднує раніше зареєстрованого слухача зміни мови.
     *
     * @param listener слухач змін локалі, якого потрібно зареєструвати або видалити.
     */
    public void removeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

/**
 * Змінює активну мову інтерфейсу та зберігає вибір користувача.
 *
 * @param language мова інтерфейсу, яку потрібно застосувати або описати.
 */
public void setLanguage(SupportedLanguage language) {
        applyLanguage(language, true);
    }

/**
 * Тимчасово змінює мову для попереднього перегляду без збереження налаштування.
 *
 * @param language мова інтерфейсу, яку потрібно застосувати або описати.
 */
public void previewLanguage(SupportedLanguage language) {
        applyLanguage(language, false);
    }

/**
 * Записує поточну мову інтерфейсу в глобальні налаштування.
 */
public void persistCurrentLanguage() {
        if (settings != null) {
            settings.save(LANGUAGE_KEY, locale.toLanguageTag());
        }
    }

    /**
     * Застосовує локаль до інтерфейсу та за потреби зберігає вибір мови.
     *
     * @param language мова інтерфейсу, яку потрібно застосувати або описати.
     * @param persist ознака, чи потрібно записати налаштування у сховище.
     */
    private void applyLanguage(SupportedLanguage language, boolean persist) {
        if (language == null) {
            return;
        }
        setLocale(language.locale(), persist);
    }

    /**
     * Установлює locale для поточного об’єкта.
     *
     * @param newLocale нова локаль, яку потрібно застосувати.
     * @param persist ознака, чи потрібно записати налаштування у сховище.
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
     * Завантажує initial locale із відповідного джерела даних.
     *
     * @param settings сховище глобальних налаштувань застосунку.
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
     * Завантажує набір текстів інтерфейсу для переданої локалі.
     *
     * @param locale локаль, для якої потрібно завантажити або показати текст.
     */
    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
