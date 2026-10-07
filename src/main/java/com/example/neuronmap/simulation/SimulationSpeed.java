package com.example.neuronmap.simulation;

/** Validates and parses the user-editable simulation tick interval. */
public final class SimulationSpeed {

    private SimulationSpeed() {
    }

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
