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
     * Створює екземпляр NeuronPresentation та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuron нейрон моделі.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
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
     * Створює екземпляр NeuronPresentation та зберігає передані залежності, потрібні для його роботи.
     *
     * @param neuron нейрон моделі.
     * @param x координата X.
     * @param y координата Y.
     * @param rotationDegrees кут обертання в градусах.
     * @param directionReversed ознака розвернутого напрямку.
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
     * @return нейрон, візуальне подання якого описує цей об’єкт.
     */
    public Neuron neuron() {
        return neuron;
    }

    /**
     * Повертає горизонтальну координату нейрона на карті.
     *
     * @return горизонтальну координату нейрона на карті.
     */
    public double x() {
        return x;
    }

    /**
     * Повертає вертикальну координату нейрона на карті.
     *
     * @return вертикальну координату нейрона на карті.
     */
    public double y() {
        return y;
    }

    /**
     * Повертає кут обертання нейрона в градусах.
     *
     * @return кут обертання нейрона в градусах.
     */
    public double rotationDegrees() {
        return rotationDegrees;
    }

    /**
     * Повертає ознаку того, що вхідний і вихідний порти нейрона розвернуті.
     *
     * @return ознаку того, що вхідний і вихідний порти нейрона розвернуті.
     */
    public boolean directionReversed() {
        return directionReversed;
    }

    /**
     * Установлює координати подання нейрона.
     *
     * @param x координата X.
     * @param y координата Y.
     */
    public void setPosition(
            double x,
            double y
    ) {
        this.x = x;
        this.y = y;
    }

    /**
     * Зміщує подання нейрона на заданий вектор.
     *
     * @param dx значення «dx», яке використовується в цьому методі.
     * @param dy значення «dy», яке використовується в цьому методі.
     */
    public void moveBy(
            double dx,
            double dy
    ) {
        x += dx;
        y += dy;
    }

    /**
     * Установлює кут обертання подання в градусах.
     *
     * @param rotationDegrees кут обертання в градусах.
     */
    public void setRotationDegrees(
            double rotationDegrees
    ) {
        this.rotationDegrees = rotationDegrees;
    }

    /**
     * Установлює, чи має нейрон розвернутий напрямок сигналу.
     *
     * @param directionReversed ознака розвернутого напрямку.
     */
    public void setDirectionReversed(
            boolean directionReversed
    ) {
        this.directionReversed = directionReversed;
    }

    /**
     * Перемикає звичайний і розвернутий напрямки сигналу.
     */
    public void toggleDirection() {
        directionReversed = !directionReversed;
    }
}
