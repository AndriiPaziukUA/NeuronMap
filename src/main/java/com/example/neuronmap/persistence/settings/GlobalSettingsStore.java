package com.example.neuronmap.persistence.settings;

import com.example.neuronmap.persistence.AtomicPropertiesWriter;
import com.example.neuronmap.persistence.PersistenceException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Читає й записує глобальні налаштування застосунку у файлі властивостей.
 */
public final class GlobalSettingsStore {

    private final Path path;

    /**
     * Створює екземпляр GlobalSettingsStore та зберігає передані залежності, потрібні для його роботи.
     *
     * @param path шлях до файла глобальних налаштувань.
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
     *
     * @return прочитані властивості або порожній набір, якщо файл ще не існує.
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
     * Передає запис властивостей спільному механізму атомарної заміни файла.
     *
     * @param properties набір властивостей, який потрібно записати у файл.
     */
    private void writeProperties(Properties properties) {
        try {
            AtomicPropertiesWriter.write(path, properties, "NeuronMap global settings");
        } catch (IOException exception) {
            throw new PersistenceException(
                    "Не вдалося зберегти глобальні налаштування.",
                    exception
            );
        }
    }
}
