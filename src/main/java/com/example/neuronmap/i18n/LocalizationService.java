package com.example.neuronmap.i18n;

import java.text.Collator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/** Керує активною мовою, підтвердженим вибором, перекладами та сповіщеннями про зміну локалі. */
public final class LocalizationService {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private final LanguagePreferenceStore languagePreferenceStore;
    private final List<Consumer<Locale>> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;
    private SupportedLanguage confirmedLanguage;

    /**
     * Створює службу локалізації зі сховищем збереженої мови.
     * Якщо значення відсутнє або некоректне, початковою мовою буде англійська.
     *
     * @param languagePreferenceStore сховище налаштування мови.
     * @throws NullPointerException якщо сховище не задано.
     */
    public LocalizationService(LanguagePreferenceStore languagePreferenceStore) {
        this.languagePreferenceStore = Objects.requireNonNull(
                languagePreferenceStore,
                "languagePreferenceStore"
        );
        this.locale = loadInitialLocale(languagePreferenceStore);
        this.bundle = loadBundle(locale);
        this.confirmedLanguage = SupportedLanguage.fromLocale(locale);
    }

    /**
     * Створює службу локалізації в пам'яті з указаною початковою мовою.
     * Вибрана мова не зберігатиметься між запусками.
     *
     * @param initialLocale початкова мова; непідтримуване або порожнє значення замінюється англійською.
     */
    public LocalizationService(Locale initialLocale) {
        this.languagePreferenceStore = null;
        this.locale = SupportedLanguage.fromLocale(initialLocale).locale();
        this.bundle = loadBundle(locale);
        this.confirmedLanguage = SupportedLanguage.fromLocale(locale);
    }

    /**
     * Повертає поточну активну локаль, яка може містити непідтверджений попередній перегляд.
     *
     * @return нормалізована активна локаль.
     */
    public Locale locale() {
        return locale;
    }

    /**
     * Повертає підтримувану мову, що відповідає поточній активній локалі.
     *
     * @return активна підтримувана мова.
     */
    public SupportedLanguage language() {
        return SupportedLanguage.fromLocale(locale);
    }

    /**
     * Повертає останню підтверджену мову, не враховуючи тимчасовий попередній перегляд.
     *
     * @return мова, до якої слід повернутися під час скасування редагування.
     */
    public SupportedLanguage confirmedLanguage() {
        return confirmedLanguage;
    }

    /**
     * Перевіряє, чи активна мова відрізняється від останньої підтвердженої.
     *
     * @return {@code true}, якщо користувач має непідтверджений попередній перегляд мови.
     */
    public boolean hasUnconfirmedLanguagePreview() {
        return language() != confirmedLanguage;
    }

    /**
     * Повертає переклад за ключем або сам ключ, якщо переклад відсутній.
     *
     * @param key ключ повідомлення в ресурсному наборі.
     * @return перекладений текст або ключ, якщо перекладу немає чи ключ порожній.
     */
    public String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    /**
     * Повертає перекладене повідомлення з підставленими аргументами.
     *
     * @param key ключ шаблону повідомлення.
     * @param arguments значення, що підставляються в шаблон.
     * @return відформатоване локалізоване повідомлення.
     */
    public String text(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    /**
     * Повертає перекладену назву підтримуваної мови.
     *
     * @param language мова, назву якої потрібно показати.
     * @return перекладена назва мови або порожній рядок, якщо мову не задано.
     */
    public String displayName(SupportedLanguage language) {
        return language == null ? "" : text(language.displayKey());
    }

    /**
     * Повертає підтримувані мови, відсортовані за локалізованими назвами.
     *
     * @return незмінний список мов у порядку відображення.
     */
    public List<SupportedLanguage> supportedLanguagesInDisplayOrder() {
        List<SupportedLanguage> languages = new ArrayList<>(List.of(SupportedLanguage.values()));
        Collator collator = Collator.getInstance(locale);
        languages.sort(Comparator.comparing(this::displayName, collator));
        return List.copyOf(languages);
    }

    /**
     * Реєструє слухача, який отримуватиме сповіщення про зміну активної локалі.
     *
     * @param listener слухач; значення {@code null} і повторна реєстрація ігноруються.
     */
    public void addListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Вилучає раніше зареєстрованого слухача зміни локалі.
     *
     * @param listener слухач, якого потрібно вилучити.
     */
    public void removeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

    /**
     * Негайно застосовує та підтверджує мову, зберігаючи її через налаштоване сховище.
     *
     * @param language мова, яку потрібно застосувати; {@code null} ігнорується.
     */
    public void setLanguage(SupportedLanguage language) {
        if (language == null) {
            return;
        }
        setLocale(language.locale(), true);
    }

    /**
     * Тимчасово застосовує мову для попереднього перегляду, не змінюючи підтверджений вибір і файл налаштувань.
     *
     * @param language мова для попереднього перегляду; {@code null} ігнорується.
     */
    public void previewLanguage(SupportedLanguage language) {
        if (language != null) {
            setLocale(language.locale(), false);
        }
    }

    /**
     * Повертає активну мову до останнього підтвердженого значення без запису у сховище.
     * Оновлення активної локалі сповіщає слухачів, тому інтерфейс синхронно перемальовує переклади.
     */
    public void discardLanguagePreview() {
        setLocale(confirmedLanguage.locale(), false);
    }

    /**
     * Підтверджує поточну активну мову та зберігає її, якщо служба має сховище.
     * Підтверджене значення оновлюється лише після успішного виклику сховища.
     */
    public void persistCurrentLanguage() {
        if (languagePreferenceStore != null) {
            languagePreferenceStore.saveLanguageTag(locale.toLanguageTag());
        }
        confirmedLanguage = language();
    }

    /**
     * Нормалізує локаль, завантажує її ресурсний набір і сповіщає слухачів лише за фактичної зміни мови.
     *
     * @param newLocale локаль, яку потрібно застосувати.
     */
    private void setLocale(Locale newLocale, boolean persist) {
        SupportedLanguage supported = SupportedLanguage.fromLocale(newLocale);
        Locale normalized = supported.locale();
        boolean changed = !normalized.getLanguage().equalsIgnoreCase(locale.getLanguage());
        if (changed) {
            locale = normalized;
            bundle = loadBundle(locale);
        }
        if (persist) {
            if (languagePreferenceStore != null) {
                languagePreferenceStore.saveLanguageTag(locale.toLanguageTag());
            }
            confirmedLanguage = SupportedLanguage.fromLocale(locale);
        }
        if (changed) {
            for (Consumer<Locale> listener : List.copyOf(listeners)) {
                listener.accept(locale);
            }
        }
    }

    /**
     * Завантажує збережений мовний тег або використовує англійську, якщо значення відсутнє чи некоректне.
     *
     * @param languagePreferenceStore сховище налаштування мови.
     * @return початкова підтримувана локаль.
     */
    private static Locale loadInitialLocale(LanguagePreferenceStore languagePreferenceStore) {
        String stored = languagePreferenceStore.loadLanguageTag();
        if (stored == null || stored.isBlank()) {
            return SupportedLanguage.ENGLISH.locale();
        }
        try {
            return SupportedLanguage.fromLocale(Locale.forLanguageTag(stored)).locale();
        } catch (RuntimeException exception) {
            return SupportedLanguage.ENGLISH.locale();
        }
    }

    /**
     * Завантажує ресурсний набір перекладів для заданої локалі.
     *
     * @param locale локаль, для якої потрібно завантажити переклади.
     * @return ресурсний набір перекладів.
     */
    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
