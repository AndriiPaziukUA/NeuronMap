package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Читає й записує глобальні налаштування застосунку у файлі властивостей.
 */
public final class GlobalSettingsStore {

    private final Path path;

    /**
     * Створює екземпляр GlobalSettingsStore та зберігає передані залежності, потрібні для його роботи.
     *
     * @param path шлях до файлу або каталогу.
     */
    public GlobalSettingsStore(Path path) {
        if (path == null) {

            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path.toAbsolutePath().normalize();
    }

    /**
     * Повертає значення глобального налаштування за ключем або null, якщо ключ не задано.
     *
     * @param key ключ налаштування або перекладу.
     *
     * @return значення глобального налаштування за ключем або null, якщо ключ не задано.
     */
    public synchronized String load(String key) {
        Properties properties = loadProperties();
        return properties.getProperty(key);
    }

    /**
     * Оновлює значення глобального налаштування й записує файл властивостей.
     *
     * @param key ключ налаштування або перекладу.
     * @param value значення, яке потрібно зберегти або перевірити.
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
     * Зчитує файл глобальних налаштувань у об’єкт Properties.
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

            throw new PersistenceException(
                    "Не вдалося прочитати глобальні налаштування.",
                    exception
            );
        }
    }

    /**
     * Записує переданий набір властивостей у файл налаштувань.
     *
     * @param properties набір властивостей, який потрібно записати у файл.
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

            throw new PersistenceException(
                    "Не вдалося зберегти глобальні налаштування.",
                    exception
            );
        }
    }
}
