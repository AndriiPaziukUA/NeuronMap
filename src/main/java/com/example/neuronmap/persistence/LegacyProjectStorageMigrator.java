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
 * Переносить дані проєктів зі старого формату сховища до поточної структури каталогів, не перезаписуючи наявні проєкти.
 */
public final class LegacyProjectStorageMigrator {

    private static final String MIGRATION_MARKER = ".legacy-storage-migrated";
    private static final String PROJECT_PREFIX = "project.";
    private static final String NAME_SUFFIX = ".name";
    private static final String FILE_SUFFIX = ".file";
    private static final String LAST_PROJECT_KEY = "last_project_id";

    private LegacyProjectStorageMigrator() {
    }

    /**
     * Перевіряє наявність старого сховища й запускає потрібну міграцію до поточної структури каталогу проєктів.
     *
     * @param storageDirectory значення «storage directory», яке використовується в цьому методі.
     * @param legacyDatabasePath значення «legacy database path», яке використовується в цьому методі.
     * @param legacyProjectsDirectory значення «legacy projects directory», яке використовується в цьому методі.
     * @param legacyCatalogPath значення «legacy catalog path», яке використовується в цьому методі.
     * @param legacyProjectName значення «legacy project name», яке використовується в цьому методі.
     * @param globalSettings значення «global settings», яке використовується в цьому методі.
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

            throw new PersistenceException(
                    "Не вдалося перенести старі проєкти NeuronMap.",
                    exception
            );
        }
    }

    /**
     * Переносить записи каталогу проєктів зі старого розташування до поточного каталогу та оновлює глобальні налаштування.
     *
     * @param storageDirectory значення «storage directory», яке використовується в цьому методі.
     * @param legacyProjectsDirectory значення «legacy projects directory», яке використовується в цьому методі.
     * @param legacyCatalogPath значення «legacy catalog path», яке використовується в цьому методі.
     * @param globalSettings значення «global settings», яке використовується в цьому методі.
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
     * Переносить застарілу базу даних проєкту в каталог сховища, якщо цільовий файл ще не створено.
     *
     * @param storageDirectory значення «storage directory», яке використовується в цьому методі.
     * @param legacyDatabasePath значення «legacy database path», яке використовується в цьому методі.
     * @param legacyProjectName значення «legacy project name», яке використовується в цьому методі.
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
     * Підбирає унікальну назву для проєкту, який переноситься зі старого сховища.
     *
     * @param storageDirectory значення «storage directory», яке використовується в цьому методі.
     * @param requestedName значення «requested name», яке використовується в цьому методі.
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
     * Створює цільовий каталог міграції або перевіряє, що він придатний для запису.
     *
     * @param directory каталог, який потрібно обробити.
     */
    private static void prepareTargetDirectory(Path directory)
            throws IOException {
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
            return;
        }

        Path database = directory.resolve("project.db");
        if (Files.isRegularFile(database)) {

            throw new IOException(
                    "Migration target already contains project.db: " + directory
            );
        }

        clearDirectory(directory);
    }

    /**
     * Очищає directory від тимчасових або застарілих значень.
     *
     * @param directory каталог, який потрібно обробити.
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
     * Копіює часову мітку останньої зміни з вихідного файла або каталогу на цільовий.
     *
     * @param source значення «source», яке використовується в цьому методі.
     * @param target цільовий вузол або об’єкт інтерфейсу, який потрібно перевірити чи знайти.
     */
    private static void copyLastModified(Path source, Path target)
            throws IOException {
        Files.setLastModifiedTime(
                target,
                Files.getLastModifiedTime(source)
        );
    }

    /**
     * Нормалізує назву проєкту перед створенням або перейменуванням.
     *
     * @param name назва, яку потрібно перевірити або зберегти.
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
