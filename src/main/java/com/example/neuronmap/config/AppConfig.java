package com.example.neuronmap.config;

/** Immutable application-level configuration loaded from XML. */
public record AppConfig(
        Window window,
        Camera camera,
        Simulation simulation
) {
    public AppConfig {
        if (window == null || camera == null || simulation == null) {
            throw new IllegalArgumentException("configuration sections must not be null");
        }
    }

    public record Window(
            String title,
            double defaultWidth,
            double defaultHeight,
            String stateFile
    ) {
        public Window {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("window title must not be blank");
            }
            if (!Double.isFinite(defaultWidth) || defaultWidth <= 0.0) {
                throw new IllegalArgumentException("defaultWidth must be positive and finite");
            }
            if (!Double.isFinite(defaultHeight) || defaultHeight <= 0.0) {
                throw new IllegalArgumentException("defaultHeight must be positive and finite");
            }
            if (stateFile == null || stateFile.isBlank()) {
                throw new IllegalArgumentException("stateFile must not be blank");
            }
        }
    }

    public record Camera(
            double zoomFactor
    ) {
        public Camera {
            if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
                throw new IllegalArgumentException("zoomFactor must be finite and greater than 1");
            }
        }
    }

    public record Simulation(
            double defaultTickMillis,
            double minTickMillis,
            double maxTickMillis
    ) {
        public Simulation {
            if (!Double.isFinite(defaultTickMillis)
                    || !Double.isFinite(minTickMillis)
                    || !Double.isFinite(maxTickMillis)) {
                throw new IllegalArgumentException("simulation timings must be finite");
            }
            if (minTickMillis <= 0.0
                    || maxTickMillis < minTickMillis
                    || defaultTickMillis < minTickMillis
                    || defaultTickMillis > maxTickMillis) {
                throw new IllegalArgumentException("invalid simulation timing range");
            }
        }
    }
}
