package com.example.neuronmap.config;

/**
 * Об’єднує налаштування вікна, камери й симуляції, завантажені з конфігурації застосунку.
 * @param window параметри головного вікна.
 * @param camera параметри зміни масштабу камери.
 * @param simulation початкова, мінімальна й максимальна тривалість такту симуляції.
 */
public record AppConfig(
        Window window,
        Camera camera,
        Simulation simulation
) {
    /**
     * Створює конфігурацію застосунку з параметрами вікна, камери й симуляції.
     *
     * @param window налаштування головного вікна застосунку.
     * @param camera налаштування масштабу камери.
     * @param simulation налаштування часу одного такту симуляції.
     */
    public AppConfig {
        if (window == null || camera == null || simulation == null) {

            throw new IllegalArgumentException("configuration sections must not be null");
        }
    }

    /**
     * Містить назву, початкові розміри та файл збереження геометрії вікна.
     * @param title назва головного вікна.
     * @param defaultWidth початкова ширина вікна.
     * @param defaultHeight початкова висота вікна.
     * @param stateFile ім’я файлу зі збереженими координатами й розмірами вікна.
     */
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

    /**
     * Зберігає коефіцієнт масштабування, який застосовується за один крок прокручування.
     * @param zoomFactor множник зміни масштабу камери за крок.
     */
    public record Camera(
            double zoomFactor
    ) {
        public Camera {
            if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {

                throw new IllegalArgumentException("zoomFactor must be finite and greater than 1");
            }
        }
    }

    /**
     * Зберігає дозволений діапазон тривалості такту та початкове значення.
     * @param defaultTickMillis початкова тривалість такту в мілісекундах.
     * @param minTickMillis мінімальна дозволена тривалість такту в мілісекундах.
     * @param maxTickMillis максимальна дозволена тривалість такту в мілісекундах.
     */
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
