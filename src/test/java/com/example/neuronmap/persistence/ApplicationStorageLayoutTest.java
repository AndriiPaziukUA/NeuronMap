package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє централізоване формування шляхів до runtime-файлів і межі каталогу зберігання. */
class ApplicationStorageLayoutTest {

    @TempDir
    private Path temporaryDirectory;

    /** Перевіряє, що глобальні налаштування та зовнішні перевизначення використовують один кореневий каталог. */
    @Test
    void centralizesRuntimeFilePaths() {
        ApplicationStorageLayout layout = new ApplicationStorageLayout(temporaryDirectory);

        assertEquals(temporaryDirectory.resolve("global.properties"), layout.globalSettingsFile());
        assertEquals(temporaryDirectory.resolve("app-config.properties"), layout.configurationOverrideFile());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), layout.rootDirectory());
    }

    /** Перевіряє, що допустиме ім'я файла вікна розміщується безпосередньо в корені сховища. */
    @Test
    void resolvesWindowStateFileInsideStorageRoot() {
        ApplicationStorageLayout layout = new ApplicationStorageLayout(temporaryDirectory);

        assertEquals(temporaryDirectory.resolve("window.properties"), layout.windowStateFile("window.properties"));
    }

    /** Перевіряє відхилення абсолютних шляхів, підкаталогів і спроб обходу кореневого каталогу. */
    @Test
    void rejectsWindowStatePathsOutsideStorageRoot() {
        ApplicationStorageLayout layout = new ApplicationStorageLayout(temporaryDirectory);

        assertThrows(IllegalArgumentException.class, () -> layout.windowStateFile("../window.properties"));
        assertThrows(IllegalArgumentException.class, () -> layout.windowStateFile("nested/window.properties"));
        assertThrows(IllegalArgumentException.class, () -> layout.windowStateFile("C:\\temp\\window.properties"));
    }
}
