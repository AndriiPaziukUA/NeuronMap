package com.example.neuronmap;

/**
 * Містить незмінні метадані бренду, які мають бути однаковими в усіх частинах застосунку.
 * Операційні параметри запуску сюди не належать і визначаються конфігурацією.
 */
public final class ApplicationMetadata {

    /** Єдине авторитетне відображуване ім'я застосунку. */
    public static final String NAME = "NeuronMap";

    /** Забороняє створювати екземпляри класу незмінних метаданих застосунку. */
    private ApplicationMetadata() {
    }
}
