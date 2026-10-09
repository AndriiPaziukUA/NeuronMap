package com.example.neuronmap.persistence;

/**
 * Зберігає розташування й розміри вікна для відновлення під час наступного запуску.
 * @param width ширина вікна в логічних пікселях.
 * @param height висота вікна в логічних пікселях.
 * @param x горизонтальна координата лівого краю вікна.
 * @param y вертикальна координата верхнього краю вікна.
 */
public record WindowState(
        double width,
        double height,
        double x,
        double y
) {

    /**
     * Перевіряє, що ширина й висота вікна є скінченними додатними числами, а координати його положення — скінченними.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
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
