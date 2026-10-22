package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionView;

/** Визначає керування візуальними імпульсами та їх синхронізацію з тактами симуляції. */
public interface PulseAnimator {

    /** Призупиняє всі активні анімації імпульсів. */
    void pauseAll();

    /** Відновлює всі призупинені анімації імпульсів. */
    void resumeAll();

    /** Зупиняє всі активні анімації імпульсів і видаляє їх з полотна. */
    void stopAll();

    /**
     * Негайно зупиняє імпульси на зв'язках, що входять до зазначеного нейрона або виходять із нього.
     *
     * @param neuronId ідентифікатор нейрона, пов'язані з яким анімації слід зупинити.
     */
    void stopForNeuron(String neuronId);

    /**
     * Перевіряє наявність анімацій, які ще виконуються.
     *
     * @return {@code true}, якщо є активні анімації; інакше {@code false}.
     */
    boolean hasActiveAnimations();

    /**
     * Запускає анімацію для заданого візуального подання зв'язку зі стандартною тривалістю такту.
     *
     * @param connectionView візуальне подання зв'язку.
     */
    void play(ConnectionView connectionView);

    /**
     * Запускає анімацію, яка завершується через заданий час, наприклад на залишку поточного такту.
     * Реалізація, що підтримує індивідуальну тривалість, має перевизначити цей метод; стандартна поведінка
     * зберігає сумісність із попередніми реалізаціями та запускає анімацію звичайним способом.
     *
     * @param connectionView візуальне подання зв'язку.
     * @param durationMillis тривалість анімації в мілісекундах.
     * @throws IllegalArgumentException якщо тривалість не є скінченним додатним числом.
     */
    default void play(ConnectionView connectionView, double durationMillis) {
        if (!Double.isFinite(durationMillis) || durationMillis <= 0.0) {
            throw new IllegalArgumentException("animation duration must be finite and positive");
        }
        play(connectionView);
    }

    /**
     * Завершує всі імпульси попереднього такту перед застосуванням нового результату симуляції.
     * Реалізації, що керують часовими шкалами самостійно, можуть залишити стандартну поведінку,
     * якщо завершення синхронізується зовнішнім механізмом.
     */
    default void completeAtTickBoundary() {
        // Для сумісних реалізацій синхронізацію може забезпечувати зовнішній механізм.
    }

    /**
     * Застосовує тривалість такту симуляції до нових і вже активних анімацій імпульсів.
     *
     * @param tickDurationMillis тривалість одного такту в мілісекундах.
     */
    void setTickDurationMillis(double tickDurationMillis);
}
