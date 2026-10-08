package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Stores project names, paths, modification timestamps and the last opened project. */
public final class ProjectCatalogStore {

    private static final String PROJECT_PREFIX = "project.";
    private static final String NAME_SUFFIX = ".name";
    private static final String FILE_SUFFIX = ".file";
    private static final String MODIFIED_SUFFIX = ".modified";
    private static final String LAST_PROJECT_KEY = "last_project_id";

    private final Path catalogPath;

    public ProjectCatalogStore(Path catalogPath) {
        if (catalogPath == null) {
            throw new IllegalArgumentException("catalogPath must not be null");
        }
        this.catalogPath = catalogPath.toAbsolutePath().normalize();
    }

    public synchronized Map<String, ProjectRecord> loadProjects() {
        Properties properties = loadProperties();
        Map<String, ProjectRecord> records = new LinkedHashMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PROJECT_PREFIX) || !key.endsWith(NAME_SUFFIX)) {
                continue;
            }

            String id = key.substring(
                    PROJECT_PREFIX.length(),
                    key.length() - NAME_SUFFIX.length()
            );
            String name = properties.getProperty(key);
            String file = properties.getProperty(
                    PROJECT_PREFIX + id + FILE_SUFFIX
            );
            long modifiedAtEpochMillis = parseLong(
                    properties.getProperty(
                            PROJECT_PREFIX + id + MODIFIED_SUFFIX
                    )
            );

            if (id.isBlank() || name == null || name.isBlank()
                    || file == null || file.isBlank()) {
                continue;
            }

            records.put(
                    id,
                    new ProjectRecord(
                            id,
                            name,
                            file,
                            modifiedAtEpochMillis
                    )
            );
        }

        return records;
    }

    public synchronized String loadLastProjectId() {
        return loadProperties().getProperty(LAST_PROJECT_KEY);
    }

    public synchronized long register(ProjectRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }

        Properties properties = loadProperties();
        String prefix = PROJECT_PREFIX + record.id();
        long modifiedAt = nextModifiedAt(
                properties,
                record.id(),
                record.modifiedAtEpochMillis()
        );
        properties.setProperty(prefix + NAME_SUFFIX, record.name());
        properties.setProperty(prefix + FILE_SUFFIX, record.fileName());
        properties.setProperty(
                prefix + MODIFIED_SUFFIX,
                Long.toString(modifiedAt)
        );
        writeProperties(properties);
        return modifiedAt;
    }

    public synchronized long rename(
            String id,
            String name,
            long modifiedAtEpochMillis
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("project id must not be blank");
        }

        Properties properties = loadProperties();
        String prefix = PROJECT_PREFIX + id;
        long modifiedAt = nextModifiedAt(
                properties,
                id,
                modifiedAtEpochMillis
        );
        properties.setProperty(prefix + NAME_SUFFIX, name);
        properties.setProperty(
                prefix + MODIFIED_SUFFIX,
                Long.toString(modifiedAt)
        );
        writeProperties(properties);
        return modifiedAt;
    }

    public synchronized void remove(String id) {
        Properties properties = loadProperties();
        String prefix = PROJECT_PREFIX + id;
        properties.remove(prefix + NAME_SUFFIX);
        properties.remove(prefix + FILE_SUFFIX);
        properties.remove(prefix + MODIFIED_SUFFIX);
        if (id.equals(properties.getProperty(LAST_PROJECT_KEY))) {
            properties.remove(LAST_PROJECT_KEY);
        }
        writeProperties(properties);
    }

    public synchronized void setLastProjectId(String projectId) {
        Properties properties = loadProperties();
        if (projectId == null || projectId.isBlank()) {
            properties.remove(LAST_PROJECT_KEY);
        } else {
            properties.setProperty(LAST_PROJECT_KEY, projectId);
        }
        writeProperties(properties);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();

        if (!Files.isRegularFile(catalogPath)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(catalogPath)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося прочитати каталог проєктів.",
                    exception
            );
        }
    }

    private void writeProperties(Properties properties) {
        try {
            Path parent = catalogPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temp = catalogPath.resolveSibling(
                    catalogPath.getFileName() + ".tmp"
            );

            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(
                        output,
                        "NeuronMap project catalog"
                );
            }

            try {
                Files.move(
                        temp,
                        catalogPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(
                        temp,
                        catalogPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося зберегти каталог проєктів.",
                    exception
            );
        }
    }

    private static long nextModifiedAt(
            Properties properties,
            String id,
            long requested
    ) {
        long current = parseLong(
                properties.getProperty(
                        PROJECT_PREFIX + id + MODIFIED_SUFFIX
                )
        );
        if (requested > current) {
            return requested;
        }
        return current == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : current + 1L;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public record ProjectRecord(
            String id,
            String name,
            String fileName,
            long modifiedAtEpochMillis
    ) {
        public ProjectRecord {
            if (id == null || id.isBlank()
                    || name == null || name.isBlank()
                    || fileName == null || fileName.isBlank()
                    || modifiedAtEpochMillis < 0L) {
                throw new IllegalArgumentException(
                        "project catalog record is incomplete"
                );
            }
        }

        public ProjectRecord(
                String id,
                String name,
                String fileName
        ) {
            this(id, name, fileName, 0L);
        }
    }
}
