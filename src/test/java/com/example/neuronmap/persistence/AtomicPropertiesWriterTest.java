package com.example.neuronmap.persistence;

import com.example.neuronmap.persistence.window.WindowState;
import com.example.neuronmap.persistence.window.WindowStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Перевіряє, що спільний механізм запису властивостей захищає попередній файл від неповного запису.
 */
final class AtomicPropertiesWriterTest {

    /**
     * Перевіряє, що помилка після часткового запису тимчасового файла не пошкоджує попередню геометрію вікна.
     *
     * @param tempDir ізольований каталог для тестових файлів.
     * @throws IOException якщо не вдалося перевірити вміст тестового каталогу.
     */
    @Test
    void interruptedSerializationPreservesExistingWindowGeometry(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("window.properties");
        WindowState expected = new WindowState(1440.0, 900.0, 125.0, 80.0);
        WindowStateStore store = new WindowStateStore(file);
        store.save(expected);

        Properties interruptedProperties = new Properties() {
            /**
             * Імітує переривання серіалізації після появи частини вмісту у тимчасовому файлі.
             *
             * @param output потік часткового запису.
             * @param comments коментар заголовка.
             * @throws IOException завжди сигналізує змодельований збій запису.
             */
            @Override
            public void store(OutputStream output, String comments) throws IOException {
                output.write("width=broken".getBytes(StandardCharsets.ISO_8859_1));
                throw new IOException("Simulated interrupted properties write");
            }
        };

        assertThrows(
                IOException.class,
                () -> AtomicPropertiesWriter.write(file, interruptedProperties, "interrupted write")
        );

        assertEquals(expected, store.load().orElseThrow());
        try (var files = Files.list(tempDir)) {
            assertEquals(1L, files.count(), "Тимчасовий файл після помилки має бути видалений");
        }
    }
}
