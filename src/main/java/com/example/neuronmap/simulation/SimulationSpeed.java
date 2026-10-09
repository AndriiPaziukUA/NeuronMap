package com.example.neuronmap.simulation;

/**
 * Перетворює введену швидкість симуляції на тривалість такту та перевіряє її допустимий діапазон.
 */
public final class SimulationSpeed {

    private SimulationSpeed() {
    }

    /**
     * Розбирає текстову тривалість такту й перевіряє, чи лежить вона в дозволеному діапазоні.
     *
     * @param text текст, який потрібно показати або розібрати.
     * @param minMillis мінімальна допустима тривалість такту в мілісекундах.
     * @param maxMillis максимальна допустима тривалість такту в мілісекундах.
     */
    public static double parseMillis(
            String text,
            double minMillis,
            double maxMillis
    ) {
        if (text == null || text.isBlank()) {

            throw new IllegalArgumentException("speed must not be blank");
        }

        try {
            return requireMillis(
                    Double.parseDouble(text.trim()),
                    minMillis,
                    maxMillis
            );
        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "speed must be numeric",
                    exception
            );
        }
    }

    /**
     * Перевіряє, що тривалість такту є скінченною та лежить між мінімальним і максимальним значеннями.
     *
     * @param millis тривалість такту в мілісекундах.
     * @param minMillis мінімальна допустима тривалість такту в мілісекундах.
     * @param maxMillis максимальна допустима тривалість такту в мілісекундах.
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

            throw new IllegalArgumentException("invalid speed range");
        }

        if (!Double.isFinite(millis)
                || millis < minMillis
                || millis > maxMillis) {

            throw new IllegalArgumentException(
                    "speed must be between " + minMillis
                            + " and " + maxMillis + " ms"
            );
        }

        return millis;
    }
}
