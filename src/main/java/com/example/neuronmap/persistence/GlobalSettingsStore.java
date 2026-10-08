package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persists application-wide settings that must not belong to any project. */
public final class GlobalSettingsStore {

    private final Path path;

    public GlobalSettingsStore(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path.toAbsolutePath().normalize();
    }

    public synchronized String load(String key) {
        Properties properties = loadProperties();
        return properties.getProperty(key);
    }

    public synchronized void save(String key, String value) {
        Properties properties = loadProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        writeProperties(properties);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося прочитати глобальні налаштування.",
                    exception
            );
        }
    }

    private void writeProperties(Properties properties) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "NeuronMap global settings");
            }

            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося зберегти глобальні налаштування.",
                    exception
            );
        }
    }
}
