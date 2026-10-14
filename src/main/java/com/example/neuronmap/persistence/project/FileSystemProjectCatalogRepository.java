package com.example.neuronmap.persistence.project;

import com.example.neuronmap.persistence.PersistenceException;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.project.ProjectDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Реалізує збереження каталогу проєктів у файловій системі та зберігає назву останнього відкритого проєкту.
 */
public final class FileSystemProjectCatalogRepository implements ProjectCatalogRepository {

    private static final String PROJECT_FILE_NAME = "project.db";
    private static final String LAST_PROJECT_NAME_KEY = "last_project_name";

    private final Path storageDirectory;
    private final GlobalSettingsStore globalSettings;

    /**
     * Створює файловий репозиторій каталогу проєктів.
     *
     * @param storageDirectory кореневий каталог збереження проєктів.
     * @param globalSettings сховище глобальних налаштувань застосунку.
     */
    public FileSystemProjectCatalogRepository(Path storageDirectory, GlobalSettingsStore globalSettings) {
        if (storageDirectory == null || globalSettings == null) {
            throw new IllegalArgumentException("storageDirectory and globalSettings must not be null");
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.globalSettings = globalSettings;
    }

    /**
     * Повертає нормалізований абсолютний шлях до кореневого каталогу проєктів.
     *
     * @return шлях до кореневого каталогу збереження проєктів.
     */
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
     * @return опис проєкту з нормалізованим шляхом і часом останньої зміни.
     * @throws IllegalArgumentException якщо шлях не задано або він не має батьківського каталогу.
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
        return new ProjectDescriptor(
                directory.toString(),
                directory.getFileName().toString(),
                normalized,
                modifiedAt
        );
    }

    /**
     * Створює опис тимчасового проєкту, визначаючи його каталог і файл бази даних у файловому сховищі.
     *
     * @param projectName нормалізована назва тимчасового проєкту.
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
            Files.setLastModifiedTime(
                    targetDirectory.resolve(PROJECT_FILE_NAME),
                    FileTime.from(Instant.now())
            );
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
            deleteRecursively(directory);
        } catch (IOException exception) {
            throw new PersistenceException("Не вдалося видалити проєкт.", exception);
        }
    }

    /**
     * Зчитує з глобальних налаштувань назву останнього відкритого проєкту.
     *
     * @return назва останнього відкритого проєкту або {@code null}, якщо її не збережено.
     */
    @Override
    public String loadLastOpenedProjectName() {
        return globalSettings.load(LAST_PROJECT_NAME_KEY);
    }

    /**
     * Зберігає в глобальних налаштуваннях назву останнього відкритого проєкту.
     *
     * @param projectName назва проєкту або {@code null}, щоб видалити збережене значення.
     */
    @Override
    public void saveLastOpenedProjectName(String projectName) {
        globalSettings.save(LAST_PROJECT_NAME_KEY, projectName);
    }

    /**
     * Створює опис каталогу, якщо в ньому є файл бази даних проєкту.
     *
     * @param directory каталог, який потрібно перевірити.
     * @return опис збереженого проєкту або {@code null}, якщо файл бази даних відсутній.
     */
    private ProjectDescriptor descriptorIfSaved(Path directory) {
        Path database = directory.resolve(PROJECT_FILE_NAME);
        return Files.isRegularFile(database) ? descriptorForDatabasePath(database) : null;
    }

    /**
     * Перевіряє цільовий каталог і звільняє його, якщо він не містить збереженого проєкту.
     *
     * @param targetDirectory цільовий каталог перейменування.
     * @throws IOException якщо каталог неможливо перевірити або видалити.
     */
    private void prepareRenameTarget(Path targetDirectory) throws IOException {
        if (Files.notExists(targetDirectory)) {
            return;
        }
        if (Files.isRegularFile(targetDirectory.resolve(PROJECT_FILE_NAME))) {
            throw new IOException("Проєкт з такою назвою вже існує: " + targetDirectory.getFileName());
        }
        deleteRecursively(targetDirectory);
    }

    /**
     * Видаляє каталог і всі його вкладені елементи.
     *
     * @param root кореневий каталог, який потрібно видалити.
     * @throws IOException якщо один із елементів неможливо видалити.
     */
    private static void deleteRecursively(Path root) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Повертає час останньої зміни файла.
     *
     * @param path шлях до файла.
     * @return час останньої зміни файла або початок епохи, якщо час прочитати не вдалося.
     */
    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }
}
