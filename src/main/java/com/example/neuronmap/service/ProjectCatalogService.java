package com.example.neuronmap.service;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.persistence.GlobalSettingsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Керує каталогом проєктів: переліком, створенням, перейменуванням, видаленням і визначенням останнього відкритого проєкту.
 */
public final class ProjectCatalogService {

    private static final String PROJECT_FILE_NAME = "project.db";
    private static final String LAST_PROJECT_NAME_KEY = "last_project_name";

    private final Path storageDirectory;
    private final GlobalSettingsStore globalSettings;

    /**
     * Створює екземпляр ProjectCatalogService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param storageDirectory значення «storage directory», яке використовується в цьому методі.
     * @param globalSettings значення «global settings», яке використовується в цьому методі.
     */
    public ProjectCatalogService(
            Path storageDirectory,
            GlobalSettingsStore globalSettings
    ) {
        if (storageDirectory == null || globalSettings == null) {

            throw new IllegalArgumentException(
                    "storageDirectory and globalSettings must not be null"
            );
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.globalSettings = globalSettings;
    }

    /**
     * Повертає доступні проєкти з каталогу сховища.
     *
     * @return доступні проєкти з каталогу сховища.
     */
    public List<ProjectDescriptor> listProjects() {
        if (!Files.isDirectory(storageDirectory)) {
            return List.of();
        }

        List<ProjectDescriptor> projects = new ArrayList<>();
        try (var stream = Files.list(storageDirectory)) {
            stream.filter(Files::isDirectory)
                    .map(this::descriptorIfSaved)
                    .filter(java.util.Objects::nonNull)
                    .forEach(projects::add);
        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Не вдалося прочитати каталог проєктів.",
                    exception
            );
        }

        projects.sort(
                Comparator.comparing(
                        ProjectDescriptor::modifiedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        ProjectDescriptor::name,
                        String.CASE_INSENSITIVE_ORDER
                )
        );
        return List.copyOf(projects);
    }

    /**
     * Створює опис проєкту для вказаного шляху до бази даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     */
    public ProjectDescriptor descriptorForDatabasePath(Path databasePath) {
        if (databasePath == null) {

            throw new IllegalArgumentException("databasePath must not be null");
        }

        Path normalized = databasePath.toAbsolutePath().normalize();
        Path directory = normalized.getParent();
        if (directory == null) {

            throw new IllegalArgumentException("Project database has no parent directory");
        }

        Instant modifiedAt = Files.isRegularFile(normalized)
                ? lastModified(normalized)
                : null;

        return new ProjectDescriptor(
                directory.toString(),
                directory.getFileName().toString(),
                normalized,
                modifiedAt
        );
    }

    /**
     * Створює опис тимчасового проєкту без негайного створення каталогу на диску.
     *
     * @param defaultName значення «default name», яке використовується в цьому методі.
     */
    public ProjectDescriptor createTransientProject(String defaultName) {
        String name = findAvailableName(defaultName);
        Path directory = storageDirectory.resolve(name);
        return new ProjectDescriptor(
                directory.toString(),
                name,
                directory.resolve(PROJECT_FILE_NAME),
                null
        );
    }

    /**
     * Перевіряє й застосовує нову назву проєкту.
     *
     * @param project опис проєкту, над яким виконується дія.
     * @param requestedName значення «requested name», яке використовується в цьому методі.
     */
    public ProjectDescriptor rename(
            ProjectDescriptor project,
            String requestedName
    ) {
        if (project == null) {

            throw new IllegalArgumentException("project must not be null");
        }

        String normalizedName = normalizeName(requestedName);
        if (project.name().equalsIgnoreCase(normalizedName)) {
            return project;
        }

        String uniqueName = findAvailableName(normalizedName, project.id());
        Path sourceDirectory = project.databasePath().getParent();
        Path targetDirectory = storageDirectory.resolve(uniqueName);

        if (sourceDirectory == null) {

            throw new IllegalArgumentException("Project database has no parent directory");
        }

        try {
            prepareRenameTarget(targetDirectory);
            Files.move(sourceDirectory, targetDirectory);
            Files.setLastModifiedTime(
                    targetDirectory.resolve(PROJECT_FILE_NAME),
                    java.nio.file.attribute.FileTime.from(Instant.now())
            );
        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Не вдалося перейменувати проєкт.",
                    exception
            );
        }

        return descriptorForDatabasePath(
                targetDirectory.resolve(PROJECT_FILE_NAME)
        );
    }

    /**
     * Видаляє  та очищає пов’язані дані.
     *
     * @param project опис проєкту, над яким виконується дія.
     */
    public void delete(ProjectDescriptor project) {
        if (project == null) {
            return;
        }

        Path directory = project.databasePath().getParent();
        if (directory == null) {
            return;
        }
        if (!storageDirectory.equals(directory.getParent())) {

            throw new IllegalArgumentException(
                    "Project directory is outside the project storage root"
            );
        }

        try {
            deleteRecursively(directory);
        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Не вдалося видалити проєкт.",
                    exception
            );
        }

        String lastName = globalSettings.load(LAST_PROJECT_NAME_KEY);
        if (lastName != null && project.name().equalsIgnoreCase(lastName)) {
            globalSettings.save(LAST_PROJECT_NAME_KEY, null);
        }
    }

    /**
     * Зберігає ідентифікатор або шлях проєкту як останній відкритий.
     *
     * @param project опис проєкту, над яким виконується дія.
     */
    public void markLastOpened(ProjectDescriptor project) {
        globalSettings.save(
                LAST_PROJECT_NAME_KEY,
                project == null ? null : project.name()
        );
    }

    /**
     * Повертає опис останнього відкритого проєкту, якщо він усе ще доступний.
     *
     * @return опис останнього відкритого проєкту, якщо він усе ще доступний.
     */
    public ProjectDescriptor lastOpenedProject() {
        String lastName = globalSettings.load(LAST_PROJECT_NAME_KEY);
        if (lastName == null || lastName.isBlank()) {
            return null;
        }

        return listProjects().stream()
                .filter(project -> lastName.equalsIgnoreCase(project.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Повертає каталог зберігання проєктів.
     *
     * @return каталог зберігання проєктів.
     */
    public Path storageDirectory() {
        return storageDirectory;
    }

    /**
     * Створює опис проєкту, якщо в каталозі вже є його збережені дані; інакше повертає null.
     *
     * @param directory каталог, який потрібно обробити.
     */
    private ProjectDescriptor descriptorIfSaved(Path directory) {
        Path database = directory.resolve(PROJECT_FILE_NAME);
        if (!Files.isRegularFile(database)) {
            return null;
        }
        return descriptorForDatabasePath(database);
    }

    /**
     * Підбирає вільну назву проєкту, щоб не перезаписати наявний проєкт.
     *
     * @param requestedName значення «requested name», яке використовується в цьому методі.
     */
    private String findAvailableName(String requestedName) {
        return findAvailableName(requestedName, null);
    }

    /**
     * Підбирає вільну назву проєкту, щоб не перезаписати наявний проєкт.
     *
     * @param requestedName значення «requested name», яке використовується в цьому методі.
     * @param excludedProjectId ідентифікатор елемента, над яким виконується дія.
     */
    private String findAvailableName(
            String requestedName,
            String excludedProjectId
    ) {
        String normalized = normalizeName(requestedName);
        List<String> existingNames = listProjects().stream()
                .filter(project -> excludedProjectId == null
                        || !excludedProjectId.equals(project.id()))
                .map(ProjectDescriptor::name)
                .toList();

        if (!containsIgnoreCase(existingNames, normalized)) {
            return normalized;
        }

        int suffix = 1;
        String candidate;
        do {
            candidate = normalized + " " + suffix++;
        } while (containsIgnoreCase(existingNames, candidate));

        return candidate;
    }

    /**
     * Перевіряє наявність назви в списку без урахування регістру літер.
     *
     * @param names значення «names», яке використовується в цьому методі.
     * @param candidate значення «candidate», яке використовується в цьому методі.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    private static boolean containsIgnoreCase(
            List<String> names,
            String candidate
    ) {
        return names.stream().anyMatch(
                name -> name.equalsIgnoreCase(candidate)
        );
    }

    /**
     * Готує цільове розташування для перейменування проєкту, перевіряючи конфлікти та каталог призначення.
     *
     * @param targetDirectory значення «target directory», яке використовується в цьому методі.
     */
    private void prepareRenameTarget(Path targetDirectory)
            throws IOException {
        if (Files.notExists(targetDirectory)) {
            return;
        }

        if (Files.isRegularFile(
                targetDirectory.resolve(PROJECT_FILE_NAME)
        )) {
            throw new IOException(
                    "Проєкт з такою назвою вже існує: " + targetDirectory.getFileName()
            );
        }

        deleteRecursively(targetDirectory);
    }

    /**
     * Видаляє каталог проєкту разом із його вмістом.
     *
     * @param root значення «root», яке використовується в цьому методі.
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
     * Нормалізує введену назву проєкту перед використанням у каталозі.
     *
     * @param name назва, яку потрібно перевірити або зберегти.
     */
    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException(
                    "project name must not be blank"
            );
        }

        String normalized = name.trim()
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "");

        if (normalized.isBlank()
                || ".".equals(normalized)
                || "..".equals(normalized)) {

            throw new IllegalArgumentException(
                    "project name is not valid"
            );
        }

        if (isReservedWindowsName(normalized)) {
            normalized = "_" + normalized;
        }
        return normalized;
    }

    /**
     * Перевіряє, чи є назва зарезервованою назвою пристрою у Windows.
     *
     * @param name назва, яку потрібно перевірити або зберегти.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    private static boolean isReservedWindowsName(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        String base = upper.contains(".")
                ? upper.substring(0, upper.indexOf('.'))
                : upper;
        return switch (base) {
            case "CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" -> true;
            default -> false;
        };
    }

    /**
     * Повертає час останньої зміни файлу або каталогу.
     *
     * @param path шлях до файлу або каталогу.
     *
     * @return час останньої зміни файлу або каталогу.
     */
    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }
}
