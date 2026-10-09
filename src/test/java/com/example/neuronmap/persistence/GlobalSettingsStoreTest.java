package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Перевіряє запис і читання загальних налаштувань.
 */
final class GlobalSettingsStoreTest {

    /**
     * Перевіряє очікувану поведінку: значення властивості файл.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
    @Test
    void valuesRoundTripThroughPropertiesFile(@TempDir Path tempDir) {
        Path path = tempDir.resolve("global.properties");
        GlobalSettingsStore store = new GlobalSettingsStore(path);

        assertNull(store.load("language"));

        store.save("language", "en");
        assertEquals("en", store.load("language"));

        store.save("language", "ru");
        assertEquals("ru", store.load("language"));

        store.save("language", null);
        assertNull(store.load("language"));
    }
}
