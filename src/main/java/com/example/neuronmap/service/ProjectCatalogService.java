package com.example.neuronmap.service;

import com.example.neuronmap.application.project.ProjectDescriptor;
import com.example.neuronmap.persistence.ProjectCatalogStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Application service for saved project metadata and project file lifecycle. */
public final class ProjectCatalogService {

    public static final String LEGACY_PROJECT_ID = "legacy";

    private final Path legacyDatabasePath;
    private final Path projectsDirectory;
    private final ProjectCatalogStore store;

    public ProjectCatalogService(Path legacyDatabasePath) {
        if (legacyDatabasePath == null) {
            throw new IllegalArgumentException(
                    "legacyDatabasePath must not be null"
            );
        }

        this.legacyDatabasePath = legacyDatabasePath
                .toAbsolutePath()
                .normalize();
        Path parent = this.legacyDatabasePath.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }

        this.projectsDirectory = parent.resolve("neuronmap-projects");
        this.store = new ProjectCatalogStore(
                parent.resolve("neuronmap-projects.properties")
        );
    }

    public java.util.Optional<ProjectDescriptor> findProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return java.util.Optional.empty();
        }
        return listProjects().stream()
                .filter(project -> projectId.equals(project.id()))
                .findFirst();
    }

    public List<ProjectDescriptor> listProjects() {
        List<ProjectDescriptor> projects = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();

        for (ProjectCatalogStore.ProjectRecord record : store.loadProjects().values()) {
            Path databasePath = resolveDatabasePath(record.fileName());
            if (!Files.isRegularFile(databasePath)) {
                missingIds.add(record.id());
                continue;
            }

            Instant modifiedAt = record.modifiedAtEpochMillis() > 0L
                    ? Instant.ofEpochMilli(record.modifiedAtEpochMillis())
                    : lastModified(databasePath);

            projects.add(new ProjectDescriptor(
                    record.id(),
                    record.name(),
                    databasePath,
                    modifiedAt
            ));
        }

        missingIds.forEach(store::remove);

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

    public void ensureLegacyProject(String name) {
        for (ProjectDescriptor project : listProjects()) {
            if (LEGACY_PROJECT_ID.equals(project.id())) {
                return;
            }
        }

        if (Files.isRegularFile(legacyDatabasePath)) {
            store.register(
                    new ProjectCatalogStore.ProjectRecord(
                            LEGACY_PROJECT_ID,
                            name,
                            legacyDatabasePath.getFileName().toString(),
                            lastModified(legacyDatabasePath).toEpochMilli()
                    )
            );
        }
    }

    public ProjectDescriptor legacyDescriptor(String name) {
        return new ProjectDescriptor(
                LEGACY_PROJECT_ID,
                name,
                legacyDatabasePath,
                Files.isRegularFile(legacyDatabasePath)
                        ? lastModified(legacyDatabasePath)
                        : null
        );
    }

    public ProjectDescriptor createTransientProject(String defaultName) {
        String id = UUID.randomUUID().toString();
        String uniqueName = uniqueName(defaultName, null);
        Path databasePath = projectsDirectory.resolve(id + ".db");
        return new ProjectDescriptor(id, uniqueName, databasePath, null);
    }

    /** Registers or refreshes a project's saved metadata timestamp. */
    public ProjectDescriptor register(ProjectDescriptor project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }

        long modifiedAt = Instant.now().toEpochMilli();
        long registeredAt = store.register(
                new ProjectCatalogStore.ProjectRecord(
                        project.id(),
                        project.name(),
                        fileNameFor(project),
                        modifiedAt
                )
        );
        return new ProjectDescriptor(
                project.id(),
                project.name(),
                project.databasePath(),
                Instant.ofEpochMilli(registeredAt)
        );
    }

    public ProjectDescriptor rename(ProjectDescriptor project, String name) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }

        String normalized = uniqueName(
                normalizeName(name),
                project.id()
        );
        long modifiedAt = store.rename(
                project.id(),
                normalized,
                Instant.now().toEpochMilli()
        );
        return new ProjectDescriptor(
                project.id(),
                normalized,
                project.databasePath(),
                Instant.ofEpochMilli(modifiedAt)
        );
    }

    public void delete(ProjectDescriptor project) {
        if (project == null) {
            return;
        }

        store.remove(project.id());
        try {
            Files.deleteIfExists(project.databasePath());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Не вдалося видалити файл проєкту.",
                    exception
            );
        }
    }

    public void markLastOpened(ProjectDescriptor project) {
        store.setLastProjectId(
                project == null ? null : project.id()
        );
    }

    public ProjectDescriptor lastOpenedProject() {
        String id = store.loadLastProjectId();
        if (id == null || id.isBlank()) {
            return null;
        }

        for (ProjectDescriptor project : listProjects()) {
            if (id.equals(project.id())) {
                return project;
            }
        }
        return null;
    }

    public Path projectsDirectory() {
        return projectsDirectory;
    }

    private String uniqueName(String requested, String excludedProjectId) {
        String normalized = normalizeName(requested);
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
            candidate = normalized + " " + suffix;
            suffix++;
        } while (containsIgnoreCase(existingNames, candidate));

        return candidate;
    }

    private static boolean containsIgnoreCase(
            List<String> names,
            String candidate
    ) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "project name must not be blank"
            );
        }
        return name.trim();
    }

    private Path resolveDatabasePath(String fileName) {
        if (fileName.equals(legacyDatabasePath.getFileName().toString())) {
            return legacyDatabasePath;
        }
        return projectsDirectory.resolve(fileName).normalize();
    }

    private String fileNameFor(ProjectDescriptor project) {
        if (LEGACY_PROJECT_ID.equals(project.id())) {
            return legacyDatabasePath.getFileName().toString();
        }
        Path fileName = project.databasePath().getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("project database path has no file name");
        }
        return fileName.toString();
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }
}
