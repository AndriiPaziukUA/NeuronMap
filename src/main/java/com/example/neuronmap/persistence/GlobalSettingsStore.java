package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Зберігає загальні налаштування застосунку окремо від конкретної карти.
 */
public final class GlobalSettingsStore {

    private final Path path;

    /**
     * Повертає результат операції «загальний налаштування зберігати».
     *
     * @param path шлях до файлу або каталогу.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public GlobalSettingsStore(Path path) {
        if (path == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path.toAbsolutePath().normalize();
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public synchronized String load(String key) {
        Properties properties = loadProperties();
        return properties.getProperty(key);
    }

    /**
     * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
     *
     * @param key ключ для пошуку або збереження значення.
     *
     * @param value значення, яке потрібно передати або зберегти.
     */
    public synchronized void save(String key, String value) {
        Properties properties = loadProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        writeProperties(properties);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «властивості».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося прочитати глобальні налаштування.",
                    exception
            );
        }
    }

    /**
     * Зберігає дані, повʼязані з «властивості», у відповідному сховищі.
     *
     * @param properties набір властивостей.
     */
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
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new PersistenceException(
                    "Не вдалося зберегти глобальні налаштування.",
                    exception
            );
        }
    }
}
