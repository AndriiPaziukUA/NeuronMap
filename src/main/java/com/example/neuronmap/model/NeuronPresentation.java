package com.example.neuronmap.model;

/**
 * Зберігає візуальне розташування нейрона: координати, кут обертання й напрямок входу та виходу.
 */
public final class NeuronPresentation {

    private final Neuron neuron;
    private double x;
    private double y;
    private double rotationDegrees;
    private boolean directionReversed;

    /**
     * Створює подання нейрона з початковими координатами, кутом і звичайним напрямком портів.
     *
     * @param neuron нейрон моделі, який представляє цей об’єкт.
     * @param x початкова горизонтальна координата на карті.
     * @param y початкова вертикальна координата на карті.
     * @param rotationDegrees початковий кут обертання в градусах.
     */
    public NeuronPresentation(
            Neuron neuron,
            double x,
            double y,
            double rotationDegrees
    ) {
        this(
                neuron,
                x,
                y,
                rotationDegrees,
                false
        );
    }

    /**
     * Створює подання нейрона з усіма параметрами початкового візуального стану.
     *
     * @param neuron нейрон моделі, який представляє цей об’єкт.
     * @param x початкова горизонтальна координата на карті.
     * @param y початкова вертикальна координата на карті.
     * @param rotationDegrees початковий кут обертання в градусах.
     * @param directionReversed {@code true}, якщо напрямок вхідного й вихідного портів розвернуто.
     * @throws IllegalArgumentException якщо нейрон не задано.
     */
    public NeuronPresentation(
            Neuron neuron,
            double x,
            double y,
            double rotationDegrees,
            boolean directionReversed
    ) {
        if (neuron == null) {

            throw new IllegalArgumentException(
                    "Neuron must not be null."
            );
        }

        this.neuron = neuron;
        this.x = x;
        this.y = y;
        this.rotationDegrees = rotationDegrees;
        this.directionReversed = directionReversed;
    }

    /**
     * Повертає нейрон, візуальне подання якого описує цей об’єкт.
     *
     * @return пов’язаний нейрон моделі.
     */
    public Neuron neuron() {
        return neuron;
    }

    /**
     * Повертає горизонтальну координату нейрона на карті.
     *
     * @return координата X.
     */
    public double x() {
        return x;
    }

    /**
     * Повертає вертикальну координату нейрона на карті.
     *
     * @return координата Y.
     */
    public double y() {
        return y;
    }

    /**
     * Повертає кут обертання нейрона в градусах.
     *
     * @return поточний кут обертання в градусах.
     */
    public double rotationDegrees() {
        return rotationDegrees;
    }

    /**
     * Повертає ознаку того, що вхідний і вихідний порти нейрона розвернуті.
     *
     * @return {@code true}, якщо напрямок портів розвернуто; інакше {@code false}.
     */
    public boolean directionReversed() {
        return directionReversed;
    }

    /**
     * Установлює координати подання нейрона на карті.
     *
     * @param x нова горизонтальна координата.
     * @param y нова вертикальна координата.
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Зміщує подання нейрона на заданий вектор у координатах карти.
     *
     * @param dx горизонтальна складова зміщення; додатне значення переміщує нейрон праворуч.
     * @param dy вертикальна складова зміщення; додатне значення переміщує нейрон униз.
     */
    public void moveBy(double dx, double dy) {
        x += dx;
        y += dy;
    }

    /**
     * Установлює кут обертання подання в градусах.
     *
     * @param rotationDegrees новий кут обертання в градусах.
     */
    public void setRotationDegrees(double rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
    }

    /**
     * Установлює, чи має нейрон розвернутий напрямок сигналу.
     *
     * @param directionReversed {@code true}, щоб розвернути вхідний і вихідний порти; інакше {@code false}.
     */
    public void setDirectionReversed(boolean directionReversed) {
        this.directionReversed = directionReversed;
    }

    /** Міняє місцями звичайний і розвернутий напрямки сигналу. */
    public void toggleDirection() {
        directionReversed = !directionReversed;
    }
}
