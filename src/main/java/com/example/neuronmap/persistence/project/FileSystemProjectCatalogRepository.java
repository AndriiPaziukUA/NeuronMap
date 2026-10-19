package com.example.neuronmap.persistence.project;

import com.example.neuronmap.persistence.FileTreeOperations;
import com.example.neuronmap.persistence.PersistenceException;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Реалізує каталог проєктів у файловій системі та зберігає назву останнього відкритого проєкту. */
public final class FileSystemProjectCatalogRepository implements ProjectCatalogRepository {

    private static final Logger LOGGER = Logger.getLogger(FileSystemProjectCatalogRepository.class.getName());
    private static final String PROJECT_FILE_NAME = "project.db";
    private static final String LAST_PROJECT_NAME_KEY = "last_project_name";

    private final Path storageDirectory;
    private final GlobalSettingsStore globalSettings;
    private final LastModifiedTimeReader lastModifiedTimeReader;

    /**
     * Створює файловий репозиторій каталогу проєктів зі стандартним читачем метаданих файлової системи.
     *
     * @param storageDirectory кореневий каталог збереження проєктів.
     * @param globalSettings сховище глобальних налаштувань застосунку.
     */
    public FileSystemProjectCatalogRepository(Path storageDirectory, GlobalSettingsStore globalSettings) {
        this(storageDirectory, globalSettings, Files::getLastModifiedTime);
    }

    /**
     * Створює репозиторій з контрольованим читачем часових міток, щоб ізольовано перевіряти помилки метаданих.
     *
     * @param storageDirectory кореневий каталог збереження проєктів.
     * @param globalSettings сховище глобальних налаштувань застосунку.
     * @param lastModifiedTimeReader читач часу останньої зміни файла.
     */
    FileSystemProjectCatalogRepository(
            Path storageDirectory,
            GlobalSettingsStore globalSettings,
            LastModifiedTimeReader lastModifiedTimeReader
    ) {
        if (storageDirectory == null || globalSettings == null || lastModifiedTimeReader == null) {
            throw new IllegalArgumentException("storageDirectory, globalSettings and lastModifiedTimeReader must not be null");
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.globalSettings = globalSettings;
        this.lastModifiedTimeReader = lastModifiedTimeReader;
    }

    /** Абстрагує читання часової мітки файла для тестування помилок доступу до метаданих. */
    @FunctionalInterface
    interface LastModifiedTimeReader {
        /**
         * Зчитує часову мітку останньої зміни файла.
         *
         * @param path шлях до файла.
         * @return час останньої зміни файла.
         * @throws IOException якщо метадані файла недоступні.
         */
        FileTime read(Path path) throws IOException;
    }

    /** Повертає нормалізований абсолютний шлях до кореневого каталогу проєктів. */
    @Override
    public Path storageDirectory() {
        return storageDirectory;
    }

    /**
     * Повертає описи каталогів, що містять файл бази даних проєкту.
     *
     * @return незмінний список описів збережених проєктів.
     * @throws PersistenceException якщо каталог проєктів не вдалося прочитати.
     */
    @Override
    public List<ProjectDescriptor> listSavedProjects() {
        if (!Files.isDirectory(storageDirectory)) {
            return List.of();
        }

        List<ProjectDescriptor> projects = new ArrayList<>();
        try (var stream = Files.list(storageDirectory)) {
            stream.filter(Files::isDirectory)
                    .map(this::descriptorIfSaved)
                    .filter(Objects::nonNull)
                    .forEach(projects::add);
        } catch (IOException exception) {
            throw new PersistenceException("Не вдалося прочитати каталог проєктів.", exception);
        }
        return List.copyOf(projects);
    }

    /**
     * Створює опис проєкту на основі шляху до його файлу бази даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @return опис проєкту з нормалізованим шляхом і часом зміни, якщо його вдалося прочитати.
     */
    @Override
    public ProjectDescriptor descriptorForDatabasePath(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        Path normalized = databasePath.toAbsolutePath().normalize();
        Path directory = normalized.getParent();
        if (directory == null) {
            throw new IllegalArgumentException("Project database has no parent directory");
        }

        Instant modifiedAt = Files.isRegularFile(normalized) ? lastModified(normalized) : null;
        return new ProjectDescriptor(directory.toString(), directory.getFileName().toString(), normalized, modifiedAt);
    }

    /**
     * Створює опис тимчасового проєкту, визначаючи каталог і файл бази даних у файловому сховищі.
     *
     * @param projectName назва тимчасового проєкту.
     * @return опис тимчасового проєкту без часу зміни.
     * @throws IllegalArgumentException якщо назву не задано або вона виходить за межі кореневого каталогу.
     */
    @Override
    public ProjectDescriptor descriptorForTransientProject(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName must not be blank");
        }
        Path directory = storageDirectory.resolve(projectName).normalize();
        if (!storageDirectory.equals(directory.getParent())) {
            throw new IllegalArgumentException("Project directory is outside the project storage root");
        }
        return new ProjectDescriptor(
                directory.toString(),
                directory.getFileName().toString(),
                directory.resolve(PROJECT_FILE_NAME),
                null
        );
    }

    /**
     * Перейменовує каталог проєкту та повертає опис із новим шляхом і назвою.
     *
     * @param project опис проєкту, який потрібно перейменувати.
     * @param newName нова назва каталогу проєкту.
     * @return опис проєкту після перейменування.
     * @throws IllegalArgumentException якщо проєкт або цільовий каталог розташовано поза коренем сховища.
     * @throws PersistenceException якщо файлову операцію перейменування не вдалося виконати.
     */
    @Override
    public ProjectDescriptor renameProject(ProjectDescriptor project, String newName) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        Path sourceDirectory = project.databasePath().getParent();
        if (sourceDirectory == null || !storageDirectory.equals(sourceDirectory.getParent())) {
            throw new IllegalArgumentException("Project directory is outside the project storage root");
        }

        Path targetDirectory = storageDirectory.resolve(newName).normalize();
        if (!storageDirectory.equals(targetDirectory.getParent())) {
            throw new IllegalArgumentException("Target directory is outside the project storage root");
        }

        try {
            prepareRenameTarget(targetDirectory);
            Files.move(sourceDirectory, targetDirectory);
            Files.setLastModifiedTime(targetDirectory.resolve(PROJECT_FILE_NAME), FileTime.from(Instant.now()));
        } catch (IOException exception) {
            throw new PersistenceException("Не вдалося перейменувати проєкт.", exception);
        }
        return descriptorForDatabasePath(targetDirectory.resolve(PROJECT_FILE_NAME));
    }

    /**
     * Видаляє каталог проєкту та всі вкладені файли й каталоги.
     *
     * @param project опис проєкту, файли якого потрібно видалити.
     * @throws IllegalArgumentException якщо каталог проєкту розташовано поза коренем сховища.
     * @throws PersistenceException якщо файли проєкту не вдалося видалити.
     */
    @Override
    public void deleteProjectFiles(ProjectDescriptor project) {
        if (project == null) {
            return;
        }
        Path directory = project.databasePath().getParent();
        if (directory == null) {
            return;
        }
        if (!storageDirectory.equals(directory.getParent())) {
            throw new IllegalArgumentException("Project directory is outside the project storage root");
        }

        try {
            FileTreeOperations.deleteOwnedDirectoryTree(storageDirectory, directory);
        } catch (IOException exception) {
            throw new PersistenceException("Не вдалося видалити проєкт.", exception);
        }
    }

    /** Зчитує з глобальних налаштувань назву останнього відкритого проєкту. */
    @Override
    public String loadLastOpenedProjectName() {
        return globalSettings.load(LAST_PROJECT_NAME_KEY);
    }

    /** Зберігає або видаляє в глобальних налаштуваннях назву останнього відкритого проєкту. */
    @Override
    public void saveLastOpenedProjectName(String projectName) {
        globalSettings.save(LAST_PROJECT_NAME_KEY, projectName);
    }

    /** Повертає опис каталогу, якщо він містить звичайний файл бази даних проєкту. */
    private ProjectDescriptor descriptorIfSaved(Path directory) {
        Path database = directory.resolve(PROJECT_FILE_NAME);
        return Files.isRegularFile(database) ? descriptorForDatabasePath(database) : null;
    }

    /**
     * Перевіряє ціль перейменування й очищає її лише тоді, коли там немає навіть символьного посилання на базу.
     *
     * @param targetDirectory цільовий каталог перейменування.
     * @throws IOException якщо каталог неможливо перевірити або видалити.
     */
    private void prepareRenameTarget(Path targetDirectory) throws IOException {
        if (Files.notExists(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.exists(targetDirectory.resolve(PROJECT_FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Проєкт з такою назвою вже існує: " + targetDirectory.getFileName());
        }
        FileTreeOperations.deleteOwnedDirectoryTree(storageDirectory, targetDirectory);
    }

    /** Повертає час зміни файла або реєструє помилку й повертає відсутнє значення. */
    private Instant lastModified(Path path) {
        try {
            return lastModifiedTimeReader.read(path).toInstant();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Не вдалося прочитати час останньої зміни файла проєкту " + path, exception);
            return null;
        }
    }
}
