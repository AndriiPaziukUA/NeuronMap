package com.example.neuronmap.model;

/**
 * Зберігає перевірений масштаб і зміщення камери карти.
 * Масштаб обмежений діапазоном, який підтримує редактор, а координати мають бути скінченними.
 *
 * @param zoom коефіцієнт масштабування карти в допустимому діапазоні.
 * @param panX скінченне горизонтальне зміщення полотна.
 * @param panY скінченне вертикальне зміщення полотна.
 */
public record CameraState(
        double zoom,
        double panX,
        double panY
) {

    /** Мінімальний масштаб, який підтримує камера редактора. */
    public static final double MIN_ZOOM = 0.25;

    /** Максимальний масштаб, який підтримує камера редактора. */
    public static final double MAX_ZOOM = 3.5;

    /**
     * Перевіряє контракт стану камери під час кожного створення запису.
     *
     * @throws IllegalArgumentException якщо масштаб не є скінченним або виходить за допустимий діапазон,
     *                                  чи якщо одна з координат панорамування не є скінченною.
     */
    public CameraState {
        if (!Double.isFinite(zoom) || zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException(
                    "zoom must be finite and within [" + MIN_ZOOM + ", " + MAX_ZOOM + "]"
            );
        }
        if (!Double.isFinite(panX)) {
            throw new IllegalArgumentException("panX must be finite");
        }
        if (!Double.isFinite(panY)) {
            throw new IllegalArgumentException("panY must be finite");
        }
    }

    /**
     * Створює початковий стан камери з масштабом 1.0 та стандартним зміщенням полотна.
     *
     * @return коректний типовий стан камери.
     */
    public static CameraState defaultState() {
        return new CameraState(1.0, 220.0, 130.0);
    }
}
