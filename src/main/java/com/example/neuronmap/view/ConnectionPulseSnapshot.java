package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/** Immutable geometry/color captured when an outgoing signal starts. */
public record ConnectionPulseSnapshot(
        Point2D start,
        Point2D end,
        Color pulseColor,
        Color normalColor
) {
    public ConnectionPulseSnapshot {
        if (start == null || end == null || pulseColor == null || normalColor == null) {
            throw new IllegalArgumentException("pulse snapshot values must not be null");
        }
    }
}
