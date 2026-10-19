package com.example.neuronmap.persistence;

import com.example.neuronmap.persistence.project.ProjectStorageDirectoryResolver;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Єдиний власник розташування runtime-файлів і кореневого каталогу даних застосунку.
 * Імена файлів визначаються тут, а не формуються незалежними викликами {@code Path.resolve} у компонентах.
 */
public final class ApplicationStorageLayout {

    private static final String GLOBAL_SETTINGS_FILE_NAME = "global.properties";
    private static final String CONFIGURATION_OVERRIDE_FILE_NAME = "app-config.properties";

    private final Path rootDirectory;

    /**
     * Створює компонування файлового сховища від заданого кореневого каталогу.
     *
     * @param rootDirectory кореневий каталог даних застосунку.
     * @throws NullPointerException якщо кореневий каталог не задано.
     */
    public ApplicationStorageLayout(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Визначає актуальне компонування сховища з централізованого правила вибору кореневого каталогу.
     *
     * @return компонування файлів застосунку для обраного кореневого каталогу.
     */
    public static ApplicationStorageLayout resolve() {
        return new ApplicationStorageLayout(ProjectStorageDirectoryResolver.resolve());
    }

    /**
     * Повертає абсолютний нормалізований кореневий каталог, спільний для даних і runtime-файлів.
     *
     * @return кореневий каталог сховища.
     */
    public Path rootDirectory() {
        return rootDirectory;
    }

    /**
     * Повертає єдиний шлях до файла глобальних налаштувань застосунку.
     *
     * @return шлях до {@code global.properties} усередині кореневого каталогу.
     */
    public Path globalSettingsFile() {
        return rootDirectory.resolve(GLOBAL_SETTINGS_FILE_NAME);
    }

    /**
     * Повертає шлях до необов'язкового файла операційних перевизначень конфігурації.
     *
     * @return шлях до {@code app-config.properties} усередині кореневого каталогу.
     */
    public Path configurationOverrideFile() {
        return rootDirectory.resolve(CONFIGURATION_OVERRIDE_FILE_NAME);
    }

    /**
     * Розміщує файл стану вікна без можливості вийти за межі кореневого каталогу.
     * Дозволено лише одне ім'я файла, оскільки геометрія вікна не повинна створювати власне дерево каталогів.
     *
     * @param fileName ім'я файла стану вікна з перевіреної конфігурації.
     * @return нормалізований абсолютний шлях до файла стану.
     * @throws NullPointerException якщо ім'я файла не задано.
     * @throws IllegalArgumentException якщо значення є абсолютним шляхом, шляхом із підкаталогом або обходом каталогу.
     */
    public Path windowStateFile(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        Path relativePath = Path.of(fileName);
        if (relativePath.isAbsolute()
                || relativePath.getNameCount() != 1
                || fileName.isBlank()
                || fileName.equals(".")
                || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains(":")) {
            throw new IllegalArgumentException("Window state must be a file name inside the storage root");
        }

        Path resolvedPath = rootDirectory.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Window state file must remain inside the storage root");
        }
        return resolvedPath;
    }
}
