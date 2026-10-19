package com.example.neuronmap.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Об'єднує перевірені налаштування головного вікна, камери й симуляції, отримані з базової та зовнішньої конфігурації.
 *
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
     * Перевіряє наявність усіх секцій конфігурації перед передаванням параметрів іншим компонентам.
     *
     * @param window налаштування головного вікна застосунку.
     * @param camera налаштування масштабу камери.
     * @param simulation налаштування часу одного такту симуляції.
     * @throws IllegalArgumentException якщо будь-яку секцію не задано.
     */
    public AppConfig {
        if (Objects.isNull(window) || Objects.isNull(camera) || Objects.isNull(simulation)) {
            throw new IllegalArgumentException("configuration sections must not be null");
        }
    }

    /**
     * Містить назву застосунку, початкові розміри та безпечне ім'я файла стану головного вікна.
     * Назва застосунку береться з {@code ApplicationMetadata}; файл геометрії розташовується в корені сховища.
     *
     * @param title незмінне відображуване ім'я застосунку.
     * @param defaultWidth початкова ширина вікна.
     * @param defaultHeight початкова висота вікна.
     * @param stateFile ім'я файла зі збереженими координатами й розмірами вікна, без каталогів.
     */
    public record Window(
            String title,
            double defaultWidth,
            double defaultHeight,
            String stateFile
    ) {
        /**
         * Перевіряє початкові розміри та забороняє шлях до стану вікна, який виходив би за межі сховища.
         *
         * @param title відображуване ім'я застосунку.
         * @param defaultWidth початкова ширина вікна.
         * @param defaultHeight початкова висота вікна.
         * @param stateFile безпечне ім'я файла стану вікна.
         * @throws IllegalArgumentException якщо назва порожня, розміри некоректні або значення файла не є ім'ям файла.
         */
        public Window {
            if (Objects.isNull(title) || title.isBlank()) {
                throw new IllegalArgumentException("window title must not be blank");
            }
            if (!Double.isFinite(defaultWidth) || defaultWidth <= 0.0) {
                throw new IllegalArgumentException("defaultWidth must be positive and finite");
            }
            if (!Double.isFinite(defaultHeight) || defaultHeight <= 0.0) {
                throw new IllegalArgumentException("defaultHeight must be positive and finite");
            }
            if (Objects.isNull(stateFile) || stateFile.isBlank()) {
                throw new IllegalArgumentException("stateFile must not be blank");
            }
            stateFile = stateFile.trim();
            Path statePath = Path.of(stateFile);
            if (statePath.isAbsolute()
                    || statePath.getNameCount() != 1
                    || stateFile.equals(".")
                    || stateFile.equals("..")
                    || stateFile.contains("/")
                    || stateFile.contains("\\")
                    || stateFile.contains(":")) {
                throw new IllegalArgumentException("stateFile must be a file name inside the storage root");
            }
        }
    }

    /**
     * Зберігає коефіцієнт масштабування, який застосовується за один крок прокручування.
     *
     * @param zoomFactor множник зміни масштабу камери за крок.
     */
    public record Camera(double zoomFactor) {
        /**
         * Перевіряє, що коефіцієнт масштабування є скінченним і збільшує масштаб.
         *
         * @param zoomFactor множник зміни масштабу камери за крок.
         * @throws IllegalArgumentException якщо коефіцієнт не є скінченним числом, більшим за одиницю.
         */
        public Camera {
            if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
                throw new IllegalArgumentException("zoomFactor must be finite and greater than 1");
            }
        }
    }

    /**
     * Зберігає дозволений діапазон тривалості такту та початкове значення.
     *
     * @param defaultTickMillis початкова тривалість такту в мілісекундах.
     * @param minTickMillis мінімальна дозволена тривалість такту в мілісекундах.
     * @param maxTickMillis максимальна дозволена тривалість такту в мілісекундах.
     */
    public record Simulation(
            double defaultTickMillis,
            double minTickMillis,
            double maxTickMillis
    ) {
        /**
         * Перевіряє скінченність і узгодженість мінімальної, типової та максимальної тривалості такту.
         *
         * @param defaultTickMillis початкова тривалість такту в мілісекундах.
         * @param minTickMillis мінімальна дозволена тривалість такту в мілісекундах.
         * @param maxTickMillis максимальна дозволена тривалість такту в мілісекундах.
         * @throws IllegalArgumentException якщо значення не скінченні або діапазон некоректний.
         */
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
