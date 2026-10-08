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

/** Owns project naming, discovery and folder/file lifecycle. */
public final class ProjectCatalogService {

    private static final String PROJECT_FILE_NAME = "project.db";
    private static final String LAST_PROJECT_NAME_KEY = "last_project_name";

    private final Path storageDirectory;
    private final GlobalSettingsStore globalSettings;

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

    public void markLastOpened(ProjectDescriptor project) {
        globalSettings.save(
                LAST_PROJECT_NAME_KEY,
                project == null ? null : project.name()
        );
    }

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

    public Path storageDirectory() {
        return storageDirectory;
    }

    private ProjectDescriptor descriptorIfSaved(Path directory) {
        Path database = directory.resolve(PROJECT_FILE_NAME);
        if (!Files.isRegularFile(database)) {
            return null;
        }
        return descriptorForDatabasePath(database);
    }

    private String findAvailableName(String requestedName) {
        return findAvailableName(requestedName, null);
    }

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

    private static boolean containsIgnoreCase(
            List<String> names,
            String candidate
    ) {
        return names.stream().anyMatch(
                name -> name.equalsIgnoreCase(candidate)
        );
    }

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

    private static void deleteRecursively(Path root) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }

        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

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

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }
}
