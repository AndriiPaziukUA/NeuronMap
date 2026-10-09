package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Фіксує геометрію й кольори зв’язку, потрібні для відображення імпульсу.
 * @param start початкова точка зв’язку у координатах сцени.
 * @param end кінцева точка зв’язку у координатах сцени.
 * @param pulseColor колір активного імпульсу.
 * @param normalColor колір зв’язку поза анімацією імпульсу.
 */
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
