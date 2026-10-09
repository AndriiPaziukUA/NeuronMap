package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Переносить дані зі старого формату зберігання, якщо вони ще є в середовищі користувача.
 */
public final class LegacyProjectStorageMigrator {

    private static final String MIGRATION_MARKER = ".legacy-storage-migrated";
    private static final String PROJECT_PREFIX = "project.";
    private static final String NAME_SUFFIX = ".name";
    private static final String FILE_SUFFIX = ".file";
    private static final String LAST_PROJECT_KEY = "last_project_id";

    /**
     * Повертає результат операції «старий формат проєкт».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private LegacyProjectStorageMigrator() {
    }

    /**
     * Виконує операцію «якщо».
     *
     * @param storageDirectory значення, що визначає каталог для цієї операції.
     *
     * @param legacyDatabasePath значення, що визначає старий формат база даних шлях для цієї операції.
     *
     * @param legacyProjectsDirectory значення, що визначає старий формат проєкти каталог для цієї операції.
     *
     * @param legacyCatalogPath значення, що визначає старий формат шлях для цієї операції.
     *
     * @param legacyProjectName значення, що визначає старий формат проєкт для цієї операції.
     *
     * @param globalSettings значення, що визначає загальний налаштування для цієї операції.
     */
    public static void migrateIfNeeded(
            Path storageDirectory,
            Path legacyDatabasePath,
            Path legacyProjectsDirectory,
            Path legacyCatalogPath,
            String legacyProjectName,
            GlobalSettingsStore globalSettings
    ) {
        if (storageDirectory == null
                || legacyDatabasePath == null
                || legacyProjectsDirectory == null
                || legacyCatalogPath == null
                || globalSettings == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("Migration paths must not be null");
        }

        Path marker = storageDirectory.resolve(MIGRATION_MARKER);
        if (Files.exists(marker)) {
            return;
        }

        try {
            Files.createDirectories(storageDirectory);
            migrateCatalogProjects(
                    storageDirectory,
                    legacyProjectsDirectory,
                    legacyCatalogPath,
                    globalSettings
            );
            migrateLegacyDatabase(
                    storageDirectory,
                    legacyDatabasePath,
                    legacyProjectName
            );
            Files.createFile(marker);
        } catch (IOException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося перенести старі проєкти NeuronMap.",
                    exception
            );
        }
    }

    /**
     * Виконує операцію «проєкти».
     *
     * @param storageDirectory значення, що визначає каталог для цієї операції.
     *
     * @param legacyProjectsDirectory значення, що визначає старий формат проєкти каталог для цієї операції.
     *
     * @param legacyCatalogPath значення, що визначає старий формат шлях для цієї операції.
     *
     * @param globalSettings значення, що визначає загальний налаштування для цієї операції.
     */
    private static void migrateCatalogProjects(
            Path storageDirectory,
            Path legacyProjectsDirectory,
            Path legacyCatalogPath,
            GlobalSettingsStore globalSettings
    ) throws IOException {
        if (!Files.isRegularFile(legacyCatalogPath)
                || !Files.isDirectory(legacyProjectsDirectory)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(legacyCatalogPath)) {
            properties.load(input);
        }

        String lastProjectId = properties.getProperty(LAST_PROJECT_KEY);

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PROJECT_PREFIX)
                    || !key.endsWith(NAME_SUFFIX)) {
                continue;
            }

            String id = key.substring(
                    PROJECT_PREFIX.length(),
                    key.length() - NAME_SUFFIX.length()
            );
            String name = properties.getProperty(key);
            String fileName = properties.getProperty(
                    PROJECT_PREFIX + id + FILE_SUFFIX
            );

            if (name == null || name.isBlank()
                    || fileName == null || fileName.isBlank()) {
                continue;
            }

            Path source = legacyProjectsDirectory.resolve(fileName).normalize();
            if (!source.startsWith(legacyProjectsDirectory.normalize())
                    || !Files.isRegularFile(source)) {
                continue;
            }

            Path targetDirectory = storageDirectory.resolve(
                    uniqueMigrationName(storageDirectory, name)
            );
            prepareTargetDirectory(targetDirectory);
            Files.copy(
                    source,
                    targetDirectory.resolve("project.db"),
                    StandardCopyOption.REPLACE_EXISTING
            );
            copyLastModified(source, targetDirectory.resolve("project.db"));

            if (id.equals(lastProjectId)) {
                globalSettings.save(
                        "last_project_name",
                        targetDirectory.getFileName().toString()
                );
            }
        }
    }

    /**
     * Виконує операцію «старий формат база даних».
     *
     * @param storageDirectory значення, що визначає каталог для цієї операції.
     *
     * @param legacyDatabasePath значення, що визначає старий формат база даних шлях для цієї операції.
     *
     * @param legacyProjectName значення, що визначає старий формат проєкт для цієї операції.
     */
    private static void migrateLegacyDatabase(
            Path storageDirectory,
            Path legacyDatabasePath,
            String legacyProjectName
    ) throws IOException {
        if (!Files.isRegularFile(legacyDatabasePath)) {
            return;
        }

        Path targetDirectory = storageDirectory.resolve(
                uniqueMigrationName(storageDirectory, legacyProjectName)
        );
        prepareTargetDirectory(targetDirectory);
        Files.copy(
                legacyDatabasePath,
                targetDirectory.resolve("project.db"),
                StandardCopyOption.REPLACE_EXISTING
        );
        copyLastModified(
                legacyDatabasePath,
                targetDirectory.resolve("project.db")
        );
    }

    /**
     * Повертає результат операції «перенесення».
     *
     * @param storageDirectory значення, що визначає каталог для цієї операції.
     *
     * @param requestedName значення, що визначає відповідну операцію для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String uniqueMigrationName(
            Path storageDirectory,
            String requestedName
    ) throws IOException {
        String baseName = normalizeName(requestedName);
        String candidate = baseName;
        int suffix = 1;

        while (Files.isDirectory(storageDirectory.resolve(candidate))
                && Files.isRegularFile(
                        storageDirectory.resolve(candidate).resolve("project.db")
                )) {
            candidate = baseName + " " + suffix++;
        }

        return candidate;
    }

    /**
     * Виконує операцію «кінцевий каталог».
     *
     * @param directory каталог для пошуку чи збереження.
     */
    private static void prepareTargetDirectory(Path directory)
            throws IOException {
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
            return;
        }

        Path database = directory.resolve("project.db");
        if (Files.isRegularFile(database)) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param directory каталог для пошуку чи збереження.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IOException(
                    "Migration target already contains project.db: " + directory
            );
        }

        clearDirectory(directory);
    }

    /**
     * Видаляє або скидає дані, повʼязані з «каталог».
     *
     * @param directory каталог для пошуку чи збереження.
     */
    private static void clearDirectory(Path directory) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(directory)) {
            stream.filter(path -> !path.equals(directory))
                    .forEach(paths::add);
        }

        paths.sort((left, right) -> Integer.compare(
                right.getNameCount(),
                left.getNameCount()
        ));

        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Виконує операцію «копіювання останній».
     *
     * @param source значення, що визначає джерело для цієї операції.
     *
     * @param target значення, що визначає кінцевий для цієї операції.
     */
    private static void copyLastModified(Path source, Path target)
            throws IOException {
        Files.setLastModifiedTime(
                target,
                Files.getLastModifiedTime(source)
        );
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Project";
        }
        String normalized = name.trim()
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "");
        return normalized.isBlank() ? "Project" : normalized;
    }
}
