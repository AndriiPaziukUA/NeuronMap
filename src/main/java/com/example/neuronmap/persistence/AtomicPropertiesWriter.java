package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Забезпечує безпечний запис набору властивостей через тимчасовий файл і заміну цільового файла.
 * Відповідає лише за файлові операції, не визначаючи політику обробки помилок конкретного сховища.
 */
public final class AtomicPropertiesWriter {

    private static final String TEMP_FILE_PREFIX = ".neuronmap-properties-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    /**
     * Забороняє створення екземплярів, оскільки механізм не зберігає внутрішнього стану.
     */
    private AtomicPropertiesWriter() {
    }

    /**
     * Записує властивості в новий тимчасовий файл і після завершення серіалізації замінює ним цільовий файл.
     * Якщо атомарне переміщення не підтримується файловою системою, використовується звичайне переміщення із заміною.
     * Тимчасовий файл створюється в тому самому каталозі, щоб за можливості забезпечити атомарну заміну.
     *
     * @param target цільовий файл, який потрібно замінити.
     * @param properties властивості для запису.
     * @param comments коментар у заголовку файла властивостей.
     * @throws IOException якщо не вдалося створити каталог або тимчасовий файл, записати дані чи замінити цільовий файл.
     * @throws NullPointerException якщо цільовий шлях або набір властивостей не задано.
     */
    public static void write(Path target, Properties properties, String comments) throws IOException {
        Path normalizedTarget = Objects.requireNonNull(target, "target must not be null")
                .toAbsolutePath()
                .normalize();
        Properties source = Objects.requireNonNull(properties, "properties must not be null");
        Path parent = normalizedTarget.getParent();
        Path temporaryFile = null;

        try {
            if (parent == null) {
                throw new IOException("Цільовий файл не має батьківського каталогу: " + normalizedTarget);
            }
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            writeTemporaryFile(temporaryFile, source, comments);
            moveIntoPlace(temporaryFile, normalizedTarget);
        } catch (IOException | RuntimeException | Error exception) {
            deleteTemporaryFile(temporaryFile, exception);
            throw exception;
        }
    }

    /**
     * Серіалізує властивості в тимчасовий файл і закриває потік перед його переміщенням.
     *
     * @param temporaryFile шлях до створеного тимчасового файла.
     * @param properties властивості, які потрібно серіалізувати.
     * @param comments коментар у заголовку файла властивостей.
     * @throws IOException якщо запис або закриття потоку завершується помилкою.
     */
    private static void writeTemporaryFile(
            Path temporaryFile,
            Properties properties,
            String comments
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            properties.store(output, comments);
        }
    }

    /**
     * Замінює цільовий файл атомарним переміщенням із резервним звичайним переміщенням.
     *
     * @param temporaryFile повністю записаний тимчасовий файл.
     * @param target цільовий файл, який потрібно замінити.
     * @throws IOException якщо обидва доступні способи переміщення не вдалися.
     */
    private static void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Видаляє тимчасовий файл після помилки, зберігаючи первинну причину збою.
     * Помилка очищення додається до первинного винятку, а не замінює його.
     *
     * @param temporaryFile тимчасовий файл, який слід видалити, або {@code null}, якщо його не створено.
     * @param failure первинна помилка, що сталася під час запису.
     */
    private static void deleteTemporaryFile(Path temporaryFile, Throwable failure) {
        if (temporaryFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }
}
