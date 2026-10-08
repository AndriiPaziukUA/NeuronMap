package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Persists window geometry independently from the SQLite map data. */
public final class WindowStateStore {

    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String X = "x";
    private static final String Y = "y";

    private final Path file;

    public WindowStateStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    public Optional<WindowState> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException ignored) {
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
        } catch (IOException ignored) {
            // Window persistence is best-effort and must never block shutdown.
        }
    }
}
