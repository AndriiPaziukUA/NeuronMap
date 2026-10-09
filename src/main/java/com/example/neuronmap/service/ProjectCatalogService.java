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
 * Знаходить збережені проєкти у файловій системі, керує їхніми назвами та порядком у списку.
 */
public final class ProjectCatalogService {

    private static final String PROJECT_FILE_NAME = "project.db";
    private static final String LAST_PROJECT_NAME_KEY = "last_project_name";

    private final Path storageDirectory;
    private final GlobalSettingsStore globalSettings;

    /**
     * Повертає результат операції «проєкт служба».
     *
     * @param storageDirectory значення, що визначає каталог для цієї операції.
     *
     * @param globalSettings значення, що визначає загальний налаштування для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ProjectCatalogService(
            Path storageDirectory,
            GlobalSettingsStore globalSettings
    ) {
        if (storageDirectory == null || globalSettings == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "storageDirectory and globalSettings must not be null"
            );
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.globalSettings = globalSettings;
    }

    /**
     * Повертає збережені проєкти, знайдені у файловій системі.
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
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
            /**
             * Повертає результат операції «стан виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Повертає результат операції «для база даних шлях».
     *
     * @param databasePath значення, що визначає база даних шлях для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ProjectDescriptor descriptorForDatabasePath(Path databasePath) {
        if (databasePath == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("databasePath must not be null");
        }

        Path normalized = databasePath.toAbsolutePath().normalize();
        Path directory = normalized.getParent();
        if (directory == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Створює обʼєкт із переданих даних «тимчасовий проєкт».
     *
     * @param defaultName значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param project опис проєкту.
     *
     * @param requestedName значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ProjectDescriptor rename(
            ProjectDescriptor project,
            String requestedName
    ) {
        if (project == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
            /**
             * Повертає результат операції «стан виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     *
     * @param project опис проєкту.
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
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "Project directory is outside the project storage root"
            );
        }

        try {
            deleteRecursively(directory);
        } catch (IOException exception) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Виконує операцію «останній».
     *
     * @param project опис проєкту.
     */
    public void markLastOpened(ProjectDescriptor project) {
        globalSettings.save(
                LAST_PROJECT_NAME_KEY,
                project == null ? null : project.name()
        );
    }

    /**
     * Повертає або знаходить дані, повʼязані з «проєкт».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «каталог».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    public Path storageDirectory() {
        return storageDirectory;
    }

    /**
     * Повертає результат операції «якщо збережений».
     *
     * @param directory каталог для пошуку чи збереження.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private ProjectDescriptor descriptorIfSaved(Path directory) {
        Path database = directory.resolve(PROJECT_FILE_NAME);
        if (!Files.isRegularFile(database)) {
            return null;
        }
        return descriptorForDatabasePath(database);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «доступний».
     *
     * @param requestedName значення, що визначає відповідну операцію для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private String findAvailableName(String requestedName) {
        return findAvailableName(requestedName, null);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «доступний».
     *
     * @param requestedName значення, що визначає відповідну операцію для цієї операції.
     *
     * @param excludedProjectId значення, що визначає проєкт ідентифікатор для цієї операції.
     *
     * @return текстове значення, сформоване або знайдене методом.
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
     * Перевіряє, чи виконується умова «ігнорувати».
     *
     * @param names значення, що визначає відповідну операцію для цієї операції.
     *
     * @param candidate значення, що визначає відповідну операцію для цієї операції.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Виконує операцію «кінцевий».
     *
     * @param targetDirectory значення, що визначає кінцевий каталог для цієї операції.
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
     * Видаляє або скидає дані, повʼязані з «відповідну операцію».
     *
     * @param root кореневий каталог.
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
     * Повертає результат операції «відповідну операцію».
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
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
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param path шлях до файлу або каталогу.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }
}
