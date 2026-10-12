package com.example.neuronmap.service;

import com.example.neuronmap.persistence.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.ProjectCatalogRepository;
import com.example.neuronmap.project.ProjectDescriptor;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Керує правилами каталогу проєктів, не виконуючи файлових операцій безпосередньо.
 */
public final class ProjectCatalogService {

    private static final String PROJECT_FILE_NAME = "project.db";

    private final ProjectCatalogRepository repository;

    /**
     * Створює службу каталогу з файловим репозиторієм для наявних місць виклику.
     *
     * @param storageDirectory кореневий каталог збереження проєктів.
     * @param globalSettings сховище глобальних налаштувань застосунку.
     */
    public ProjectCatalogService(Path storageDirectory, GlobalSettingsStore globalSettings) {
        this(new FileSystemProjectCatalogRepository(storageDirectory, globalSettings));
    }

    /**
     * Створює службу каталогу з переданим репозиторієм.
     *
     * @param repository репозиторій для доступу до збережених проєктів і метаданих каталогу.
     */
    public ProjectCatalogService(ProjectCatalogRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /**
     * Повертає збережені проєкти, упорядковані за часом зміни та назвою.
     *
     * @return незмінний список доступних збережених проєктів.
     */
    public List<ProjectDescriptor> listProjects() {
        return repository.listSavedProjects().stream()
                .sorted(Comparator.comparing(
                        ProjectDescriptor::modifiedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(ProjectDescriptor::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Створює опис проєкту для заданого шляху до бази даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @return опис проєкту, що відповідає заданому шляху.
     */
    public ProjectDescriptor descriptorForDatabasePath(Path databasePath) {
        return repository.descriptorForDatabasePath(databasePath);
    }

    /**
     * Створює опис тимчасового проєкту без негайного створення каталогу на диску.
     *
     * @param defaultName початкова назва нового проєкту.
     * @return опис тимчасового проєкту з доступною назвою.
     */
    public ProjectDescriptor createTransientProject(String defaultName) {
        String name = findAvailableName(defaultName);
        Path directory = repository.storageDirectory().resolve(name);
        return new ProjectDescriptor(
                directory.toString(),
                name,
                directory.resolve(PROJECT_FILE_NAME),
                null
        );
    }

    /**
     * Перевіряє та застосовує нову назву збереженого проєкту.
     *
     * @param project опис проєкту, який потрібно перейменувати.
     * @param requestedName запитана нова назва проєкту.
     * @return опис проєкту після перейменування або початковий опис, якщо назва не змінилася.
     */
    public ProjectDescriptor rename(ProjectDescriptor project, String requestedName) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }

        String normalizedName = normalizeName(requestedName);
        if (project.name().equalsIgnoreCase(normalizedName)) {
            return project;
        }

        String uniqueName = findAvailableName(normalizedName, project.id());
        return repository.renameProject(project, uniqueName);
    }

    /**
     * Видаляє проєкт і очищує його назву з налаштування останнього відкритого проєкту, якщо потрібно.
     *
     * @param project опис проєкту, який потрібно видалити.
     */
    public void delete(ProjectDescriptor project) {
        if (project == null) {
            return;
        }

        repository.deleteProjectFiles(project);
        String lastName = repository.loadLastOpenedProjectName();
        if (lastName != null && project.name().equalsIgnoreCase(lastName)) {
            repository.saveLastOpenedProjectName(null);
        }
    }

    /**
     * Зберігає назву проєкту як останнього відкритого.
     *
     * @param project опис проєкту або {@code null}, щоб очистити значення.
     */
    public void markLastOpened(ProjectDescriptor project) {
        repository.saveLastOpenedProjectName(project == null ? null : project.name());
    }

    /**
     * Повертає опис останнього відкритого проєкту, якщо він досі доступний.
     *
     * @return опис останнього відкритого проєкту або {@code null}, якщо такого проєкту немає.
     */
    public ProjectDescriptor lastOpenedProject() {
        String lastName = repository.loadLastOpenedProjectName();
        if (lastName == null || lastName.isBlank()) {
            return null;
        }
        return listProjects().stream()
                .filter(project -> lastName.equalsIgnoreCase(project.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Повертає кореневий каталог зберігання проєктів.
     *
     * @return кореневий каталог зберігання проєктів.
     */
    public Path storageDirectory() {
        return repository.storageDirectory();
    }

    /**
     * Підбирає вільну назву проєкту, щоб не конфліктувати з наявними збереженими проєктами.
     *
     * @param requestedName запитана назва проєкту.
     * @return доступна назва проєкту.
     */
    private String findAvailableName(String requestedName) {
        return findAvailableName(requestedName, null);
    }

    /**
     * Підбирає вільну назву проєкту, виключаючи за потреби поточний проєкт зі списку конфліктів.
     *
     * @param requestedName запитана назва проєкту.
     * @param excludedProjectId ідентифікатор проєкту, який не потрібно враховувати під час перевірки.
     * @return доступна назва проєкту.
     */
    private String findAvailableName(String requestedName, String excludedProjectId) {
        String normalized = normalizeName(requestedName);
        List<String> existingNames = listProjects().stream()
                .filter(project -> excludedProjectId == null || !excludedProjectId.equals(project.id()))
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
     * Перевіряє наявність назви в списку без урахування регістру.
     *
     * @param names список наявних назв.
     * @param candidate назва, яку потрібно перевірити.
     * @return {@code true}, якщо назва вже є у списку; інакше {@code false}.
     */
    private static boolean containsIgnoreCase(List<String> names, String candidate) {
        return names.stream().anyMatch(name -> name.equalsIgnoreCase(candidate));
    }

    /**
     * Нормалізує назву проєкту перед використанням у файловому каталозі.
     *
     * @param name назва, яку потрібно нормалізувати.
     * @return нормалізована назва проєкту.
     * @throws IllegalArgumentException якщо назва порожня або неприпустима.
     */
    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("project name must not be blank");
        }

        String normalized = name.trim()
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "");
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("project name is not valid");
        }
        if (isReservedWindowsName(normalized)) {
            normalized = "_" + normalized;
        }
        return normalized;
    }

    /**
     * Перевіряє, чи є назва зарезервованою назвою пристрою у Windows.
     *
     * @param name назва проєкту.
     * @return {@code true}, якщо назва зарезервована у Windows; інакше {@code false}.
     */
    private static boolean isReservedWindowsName(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        String base = upper.contains(".") ? upper.substring(0, upper.indexOf('.')) : upper;
        return switch (base) {
            case "CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" -> true;
            default -> false;
        };
    }
}
