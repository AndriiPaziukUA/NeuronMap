package com.example.neuronmap.persistence.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє пріоритет перевизначень каталогу даних і збереження типового шляху Documents/NeuronMap. */
class ProjectStorageDirectoryResolverTest {

    /** Перевіряє типовий шлях, коли жодне явне перевизначення не задано. */
    @Test
    void usesDocumentsAsDefaultStorageDirectory() {
        Path resolved = ProjectStorageDirectoryResolver.resolve(null, null, "/home/neurons");

        assertEquals(Path.of("/home/neurons/Documents/NeuronMap").toAbsolutePath().normalize(), resolved);
    }

    /** Перевіряє, що system property має пріоритет над змінною середовища. */
    @Test
    void systemPropertyOverridesEnvironmentVariable() {
        Path resolved = ProjectStorageDirectoryResolver.resolve("/opt/neuronmap-data", "/mnt/neuronmap", null);

        assertEquals(Path.of("/opt/neuronmap-data").toAbsolutePath().normalize(), resolved);
    }

    /** Перевіряє використання змінної середовища, якщо system property відсутній. */
    @Test
    void environmentVariableOverridesDefaultDirectory() {
        Path resolved = ProjectStorageDirectoryResolver.resolve(null, "/mnt/neuronmap", "/home/neurons");

        assertEquals(Path.of("/mnt/neuronmap").toAbsolutePath().normalize(), resolved);
    }

    /** Перевіряє явну помилку, якщо немає ані перевизначення, ані домашнього каталогу. */
    @Test
    void rejectsUnavailableHomeWhenNoOverrideExists() {
        assertThrows(IllegalStateException.class,
                () -> ProjectStorageDirectoryResolver.resolve(null, " ", null));
    }
}
