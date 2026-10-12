package com.example.neuronmap.view;

/**
 * Визначає операції керування візуальними анімаціями імпульсів.
 */
public interface PulseAnimator {

    /** Призупиняє всі активні анімації імпульсів. */
    void pauseAll();

    /** Відновлює всі призупинені анімації імпульсів. */
    void resumeAll();

    /** Зупиняє всі активні анімації імпульсів і видаляє їх з полотна. */
    void stopAll();

    /**
     * Перевіряє наявність анімацій, які ще виконуються.
     *
     * @return {@code true}, якщо є активні анімації; інакше {@code false}.
     */
    boolean hasActiveAnimations();

    /**
     * Запускає анімацію для заданого візуального подання зв’язку.
     *
     * @param connectionView візуальне подання зв’язку.
     */
    void play(ConnectionView connectionView);
}
