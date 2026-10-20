package com.example.neuronmap.view.simulation;

import com.example.neuronmap.view.connection.ConnectionView;

/** Визначає операції керування візуальними анімаціями імпульсів. */
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
     * Запускає анімацію для заданого візуального подання зв'язку.
     *
     * @param connectionView візуальне подання зв'язку.
     */
    void play(ConnectionView connectionView);

    /**
     * Застосовує тривалість такту симуляції до нових і вже активних анімацій імпульсів.
     *
     * @param tickDurationMillis тривалість одного такту в мілісекундах.
     */
    void setTickDurationMillis(double tickDurationMillis);
}
