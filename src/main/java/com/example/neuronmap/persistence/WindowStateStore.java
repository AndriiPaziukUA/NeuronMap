package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Завантажує та зберігає геометрію головного вікна в окремому файлі, не перериваючи завершення застосунку,
 * якщо необов’язковий запис стану не вдався.
 */
public final class WindowStateStore {

    private static final Logger LOGGER = Logger.getLogger(WindowStateStore.class.getName());

    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String X = "x";
    private static final String Y = "y";

    private final Path file;

    /**
     * Створює сховище геометрії вікна для заданого файла налаштувань.
     *
     * @param file шлях до файла, у якому зберігатиметься геометрія вікна.
     * @throws IllegalArgumentException якщо шлях не задано.
     */
    public WindowStateStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    /**
     * Завантажує збережену геометрію вікна, якщо файл містить коректний стан.
     *
     * @return збережений стан вікна або порожній результат, якщо файл відсутній чи містить некоректні дані.
     */
    public Optional<WindowState> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Не вдалося прочитати геометрію вікна з файлу " + file,
                    exception
            );
            return Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }

        try {
            WindowState state = new WindowState(
                    Double.parseDouble(properties.getProperty(WIDTH)),
                    Double.parseDouble(properties.getProperty(HEIGHT)),
                    Double.parseDouble(properties.getProperty(X)),
                    Double.parseDouble(properties.getProperty(Y))
            );

            return state.isValid()
                    ? Optional.of(state)
                    : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Записує коректну геометрію вікна у файл стану. Помилку запису реєструє в журналі, оскільки вона не повинна
     * перешкоджати закриттю вікна та звільненню інших ресурсів застосунку.
     *
     * @param state стан геометрії вікна, який потрібно зберегти.
     * @throws IllegalArgumentException якщо стан не задано.
     */
    public void save(WindowState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (!state.isValid()) {
            return;
        }

        Path parent = file.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Properties properties = new Properties();
            properties.setProperty(WIDTH, Double.toString(state.width()));
            properties.setProperty(HEIGHT, Double.toString(state.height()));
            properties.setProperty(X, Double.toString(state.x()));
            properties.setProperty(Y, Double.toString(state.y()));

            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "NeuronMap window geometry");
            }
        } catch (IOException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Не вдалося зберегти геометрію вікна у файлі " + file,
                    exception
            );
        }
    }
}
