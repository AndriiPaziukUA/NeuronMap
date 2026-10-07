package com.example.neuronmap.persistence;

/** Persisted application window geometry. */
public record WindowState(
        double width,
        double height,
        double x,
        double y
) {
    public boolean isValid() {
        return Double.isFinite(width)
                && Double.isFinite(height)
                && width > 0.0
                && height > 0.0
                && Double.isFinite(x)
                && Double.isFinite(y);
    }
}
