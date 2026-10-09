package com.example.neuronmap.config;

/**
 * Повертає результат операції «конфігурація».
 *
 * @param window значення, що визначає вікно для цієї операції.
 *
 * @param camera значення, що визначає камера для цієї операції.
 *
 * @param simulation значення, що визначає відповідну операцію для цієї операції.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Містить налаштування, які застосунок завантажує під час запуску.
 */
public record AppConfig(
        Window window,
        Camera camera,
        Simulation simulation
) {
    public AppConfig {
        if (window == null || camera == null || simulation == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("configuration sections must not be null");
        }
    }

    /**
     * Повертає результат операції «вікно».
     *
     * @param title значення, що визначає відповідну операцію для цієї операції.
     *
     * @param defaultWidth значення, що визначає ширина для цієї операції.
     *
     * @param defaultHeight значення, що визначає висота для цієї операції.
     *
     * @param stateFile значення, що визначає стан файл для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент Window у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    public record Window(
            String title,
            double defaultWidth,
            double defaultHeight,
            String stateFile
    ) {
        public Window {
            if (title == null || title.isBlank()) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("window title must not be blank");
            }
            if (!Double.isFinite(defaultWidth) || defaultWidth <= 0.0) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("defaultWidth must be positive and finite");
            }
            if (!Double.isFinite(defaultHeight) || defaultHeight <= 0.0) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("defaultHeight must be positive and finite");
            }
            if (stateFile == null || stateFile.isBlank()) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("stateFile must not be blank");
            }
        }
    }

    /**
     * Компонент Camera у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «камера».
     *
     * @param zoomFactor значення, що визначає масштаб для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public record Camera(
            double zoomFactor
    ) {
        public Camera {
            if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("zoomFactor must be finite and greater than 1");
            }
        }
    }

    /**
     * Компонент Simulation у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param defaultTickMillis значення, що визначає такт для цієї операції.
     *
     * @param minTickMillis значення, що визначає такт для цієї операції.
     *
     * @param maxTickMillis значення, що визначає такт для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("simulation timings must be finite");
            }
            if (minTickMillis <= 0.0
                    || maxTickMillis < minTickMillis
                    || defaultTickMillis < minTickMillis
                    || defaultTickMillis > maxTickMillis) {
                /**
                 * Повертає результат операції «виняток».
                 *
                 * @return значення або обʼєкт, визначений описаною операцією.
                 */
                throw new IllegalArgumentException("invalid simulation timing range");
            }
        }
    }
}
