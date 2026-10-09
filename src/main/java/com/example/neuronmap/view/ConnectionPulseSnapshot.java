package com.example.neuronmap.view;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Повертає результат операції «звʼязок імпульс знімок».
 *
 * @param start значення, що визначає запуск для цієї операції.
 *
 * @param end значення, що визначає відповідну операцію для цієї операції.
 *
 * @param pulseColor значення, що визначає імпульс для цієї операції.
 *
 * @param normalColor значення, що визначає відповідну операцію для цієї операції.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Зберігає геометричний знімок звʼязку, за яким рухається графічний імпульс.
 */
public record ConnectionPulseSnapshot(
        Point2D start,
        Point2D end,
        Color pulseColor,
        Color normalColor
) {
    public ConnectionPulseSnapshot {
        if (start == null || end == null || pulseColor == null || normalColor == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("pulse snapshot values must not be null");
        }
    }
}
