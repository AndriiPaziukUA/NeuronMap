package com.example.neuronmap.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє запис і відновлення геометрії вікна.
 */
class WindowStateStoreTest {

    /**
     * Перевіряє запис і відновлення геометрії вікна.
     */
    @Test
    void savesAndLoadsWindowGeometry(@TempDir Path tempDir) {
        Path file = tempDir.resolve("window.properties");
        WindowStateStore store = new WindowStateStore(file);

        WindowState expected = new WindowState(
                1440.0,
                900.0,
                125.0,
                80.0
        );

        store.save(expected);

        assertEquals(
                expected,
                store.load().orElseThrow()
        );
    }

    @Test
    void missingOrInvalidStateFallsBackToEmpty(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("window.properties");
        WindowStateStore store = new WindowStateStore(file);

        assertTrue(store.load().isEmpty());

        java.nio.file.Files.writeString(
                file,
                "width=broken\nheight=900\nx=10\ny=20\n"
        );

        assertTrue(store.load().isEmpty());
    }
}
