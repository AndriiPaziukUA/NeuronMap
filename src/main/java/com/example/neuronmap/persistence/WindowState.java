package com.example.neuronmap.persistence;

/**
 * Повертає результат операції «вікно стан».
 *
 * @param width ширина області.
 *
 * @param height висота області.
 *
 * @param x координата по горизонталі.
 *
 * @param y координата по вертикалі.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Описує геометрію вікна, потрібну для його відновлення.
 */
public record WindowState(
        double width,
        double height,
        double x,
        double y
) {
    /**
     * Перевіряє, чи виконується умова «коректний».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isValid() {
        return Double.isFinite(width)
                && Double.isFinite(height)
                && width > 0.0
                && height > 0.0
                && Double.isFinite(x)
                && Double.isFinite(y);
    }
}
