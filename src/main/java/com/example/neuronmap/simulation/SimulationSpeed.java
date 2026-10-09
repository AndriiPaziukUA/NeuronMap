package com.example.neuronmap.simulation;

/**
 * Описує швидкість симуляції та перевіряє її допустимі межі.
 */
public final class SimulationSpeed {

    /**
     * Повертає результат операції «швидкість».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private SimulationSpeed() {
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param text текст, який потрібно показати або обробити.
     *
     * @param minMillis значення, що визначає відповідну операцію для цієї операції.
     *
     * @param maxMillis значення, що визначає відповідну операцію для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public static double parseMillis(
            String text,
            double minMillis,
            double maxMillis
    ) {
        if (text == null || text.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("speed must not be blank");
        }

        try {
            return requireMillis(
                    Double.parseDouble(text.trim()),
                    minMillis,
                    maxMillis
            );
        } catch (NumberFormatException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "speed must be numeric",
                    exception
            );
        }
    }

    /**
     * Повертає результат операції «потребувати».
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     *
     * @param minMillis значення, що визначає відповідну операцію для цієї операції.
     *
     * @param maxMillis значення, що визначає відповідну операцію для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public static double requireMillis(
            double millis,
            double minMillis,
            double maxMillis
    ) {
        if (!Double.isFinite(minMillis)
                || !Double.isFinite(maxMillis)
                || minMillis <= 0.0
                || maxMillis < minMillis) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("invalid speed range");
        }

        if (!Double.isFinite(millis)
                || millis < minMillis
                || millis > maxMillis) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "speed must be between " + minMillis
                            + " and " + maxMillis + " ms"
            );
        }

        return millis;
    }
}
