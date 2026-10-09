package com.example.neuronmap.persistence;

/**
 * Зберігає масштаб і зміщення камери карти.
 * @param zoom коефіцієнт масштабування карти.
 * @param panX горизонтальне зміщення полотна.
 * @param panY вертикальне зміщення полотна.
 */
public record CameraState(
        double zoom,
        double panX,
        double panY
) {

    /**
     * Створює початковий стан камери з масштабом 1.0 та стандартним зміщенням полотна.
     */
    public static CameraState defaultState() {
        return new CameraState(
                1.0,
                220.0,
                130.0
        );
    }
}
