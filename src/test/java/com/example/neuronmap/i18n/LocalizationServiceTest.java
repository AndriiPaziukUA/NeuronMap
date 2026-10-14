package com.example.neuronmap.i18n;

import com.example.neuronmap.persistence.settings.GlobalSettingsLanguagePreferenceStore;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє початкову мову, перемикання локалей, сповіщення слухачів і узгодженість ресурсних наборів.
 */
final class LocalizationServiceTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Перевіряє, що за відсутності збереженого вибору початковою мовою стає англійська.
     */
    @Test
    void missingLanguagePreferenceDefaultsToEnglish() {
        LanguagePreferenceStore preferences = new GlobalSettingsLanguagePreferenceStore(
                new GlobalSettingsStore(temporaryDirectory.resolve("missing-settings.properties"))
        );
        LocalizationService localization = new LocalizationService(preferences);

        assertEquals(SupportedLanguage.ENGLISH, localization.language());
        assertEquals("New Project", localization.text("menu.new_project"));
    }

    /**
     * Перевіряє, що служба локалізації працює через абстракцію та зберігає мову лише на вимогу.
     */
    @Test
    void languagePreferenceIsPersistedThroughThePort() {
        GlobalSettingsStore settings = new GlobalSettingsStore(
                temporaryDirectory.resolve("language-settings.properties")
        );
        LanguagePreferenceStore preferences = new GlobalSettingsLanguagePreferenceStore(settings);
        LocalizationService localization = new LocalizationService(preferences);

        localization.setLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals("uk", settings.load("language"));

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals("uk", settings.load("language"));

        localization.persistCurrentLanguage();
        assertEquals("ru", settings.load("language"));
    }

    /**
     * Перевіряє, що служба локалізації не залежить від конкретного класу глобального сховища.
     */
    @Test
    void doesNotDependOnConcreteGlobalSettingsStore() {
        boolean hasConcreteField = Arrays.stream(LocalizationService.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == GlobalSettingsStore.class);
        boolean hasConcreteConstructor = Arrays.stream(LocalizationService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type == GlobalSettingsStore.class);
        boolean hasConcreteMethodSignature = Arrays.stream(LocalizationService.class.getDeclaredMethods())
                .anyMatch(method -> methodHasConcreteSettingsType(method));

        assertFalse(hasConcreteField || hasConcreteConstructor || hasConcreteMethodSignature);
    }

    /**
     * Перевіряє типи параметрів і результату методу на залежність від конкретного сховища.
     *
     * @param method метод, сигнатуру якого потрібно перевірити.
     * @return {@code true}, якщо сигнатура містить {@link GlobalSettingsStore}.
     */
    private static boolean methodHasConcreteSettingsType(Method method) {
        return method.getReturnType() == GlobalSettingsStore.class
                || Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type == GlobalSettingsStore.class);
    }

    /**
     * Перевіряє сортування назв підтримуваних мов відповідно до активної локалі.
     */
    @Test
    void languageNamesAreSortedUsingCurrentLanguage() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));

        localization.setLanguage(SupportedLanguage.ENGLISH);
        assertEquals(List.of(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.RUSSIAN,
                SupportedLanguage.UKRAINIAN
        ), localization.supportedLanguagesInDisplayOrder());

        localization.setLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(List.of(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.RUSSIAN,
                SupportedLanguage.UKRAINIAN
        ), localization.supportedLanguagesInDisplayOrder());
        assertEquals("Английский", localization.displayName(SupportedLanguage.ENGLISH));
        assertEquals("Русский", localization.displayName(SupportedLanguage.RUSSIAN));
        assertEquals("Украинский", localization.displayName(SupportedLanguage.UKRAINIAN));
    }

    /**
     * Перевіряє, що зміна мови сповіщає слухачів і завантажує відповідний набір перекладів.
     */
    @Test
    void switchingLanguageNotifiesListeners() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        Locale[] observed = new Locale[1];
        localization.addListener(locale -> observed[0] = locale);

        localization.setLanguage(SupportedLanguage.ENGLISH);

        assertEquals(Locale.ENGLISH, observed[0]);
        assertEquals("New Project", localization.text("menu.new_project"));
    }

    /**
     * Перевіряє, що попередній перегляд мови дозволяє змінити її та повернути попереднє значення.
     */
    @Test
    void previewLanguageDoesNotPersistAndCanBeReverted() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.RUSSIAN);
        assertEquals(SupportedLanguage.RUSSIAN, localization.language());

        localization.previewLanguage(SupportedLanguage.UKRAINIAN);
        assertEquals(SupportedLanguage.UKRAINIAN, localization.language());
    }

    /**
     * Перевіряє наявність нових підписів меню в усіх підтримуваних ресурсних наборах і відсутність вилученої підказки.
     */
    @Test
    void menuTranslationsAreConsistentAcrossSupportedLocales() {
        for (Locale locale : List.of(
                Locale.ROOT,
                Locale.ENGLISH,
                Locale.forLanguageTag("ru"),
                Locale.forLanguageTag("uk"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            assertTrue(bundle.containsKey("menu.continue"));
            assertTrue(bundle.containsKey("common.save"));
            assertFalse(bundle.containsKey("menu.open_hint"));
        }
        LocalizationService ukrainian = new LocalizationService(Locale.forLanguageTag("uk"));
        assertEquals("Продовжити", ukrainian.text("menu.continue"));
    }

    /**
     * Перевіряє, що непідтримувані або порожні локалі замінюються англійською.
     */
    @Test
    void unsupportedLocalesFallBackToEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromLocale(null));
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromLocale(Locale.forLanguageTag("fr")));
    }
}
