package com.example.neuronmap.persistence.project;

import java.nio.file.Path;

/**
 * Визначає кореневий каталог даних застосунку за явним перевизначенням або типовим шляхом користувача.
 * Значення system property має вищий пріоритет за змінну середовища.
 */
public final class ProjectStorageDirectoryResolver {

    /** System property для вибору кореневого каталогу даних. */
    public static final String STORAGE_DIRECTORY_PROPERTY = "neuronmap.storage.directory";

    /** Змінна середовища для вибору кореневого каталогу даних. */
    public static final String STORAGE_DIRECTORY_ENVIRONMENT_VARIABLE = "NEURONMAP_STORAGE_DIRECTORY";

    private static final String DOCUMENTS_DIRECTORY = "Documents";
    private static final String APPLICATION_DIRECTORY = "NeuronMap";

    /** Забороняє створювати екземпляри резолвера каталогу сховища. */
    private ProjectStorageDirectoryResolver() {
    }

    /**
     * Повертає кореневий каталог даних із пріоритетом system property, змінної середовища й типового шляху.
     *
     * @return абсолютний нормалізований кореневий каталог даних застосунку.
     * @throws IllegalStateException якщо для типового шляху недоступний домашній каталог користувача.
     */
    public static Path resolve() {
        return resolve(
                System.getProperty(STORAGE_DIRECTORY_PROPERTY),
                System.getenv(STORAGE_DIRECTORY_ENVIRONMENT_VARIABLE),
                System.getProperty("user.home")
        );
    }

    /**
     * Визначає кореневий каталог за переданими значеннями, щоб правило вибору можна було перевіряти незалежно від ОС.
     *
     * @param systemProperty значення system property, якщо його задано.
     * @param environmentVariable значення змінної середовища, якщо її задано.
     * @param userHome домашній каталог для формування типового шляху.
     * @return абсолютний нормалізований каталог.
     * @throws IllegalStateException якщо перевизначення не задано, а домашній каталог недоступний.
     */
    static Path resolve(String systemProperty, String environmentVariable, String userHome) {
        String override = firstNonBlank(systemProperty, environmentVariable);
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException("User home directory is unavailable and no storage override was supplied");
        }

        return Path.of(userHome)
                .resolve(DOCUMENTS_DIRECTORY)
                .resolve(APPLICATION_DIRECTORY)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Обирає перше непорожнє значення, зберігаючи порядок пріоритету аргументів.
     *
     * @param higherPriority значення з вищим пріоритетом.
     * @param lowerPriority значення з нижчим пріоритетом.
     * @return перше непорожнє значення або {@code null}, якщо обидва значення не задані.
     */
    private static String firstNonBlank(String higherPriority, String lowerPriority) {
        if (higherPriority != null && !higherPriority.isBlank()) {
            return higherPriority;
        }
        if (lowerPriority != null && !lowerPriority.isBlank()) {
            return lowerPriority;
        }
        return null;
    }
}
