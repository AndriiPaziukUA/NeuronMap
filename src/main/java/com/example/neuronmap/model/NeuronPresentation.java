package com.example.neuronmap.model;

/**
 * Зберігає параметри відображення нейрона: координати, кут повороту та напрямок.
 */
public final class NeuronPresentation {

    private final Neuron neuron;
    private double x;
    private double y;
    private double rotationDegrees;
    private boolean directionReversed;

    /**
     * Повертає результат операції «нейрон представлення».
     *
     * @param neuron нейрон, над яким виконується операція.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Повертає результат операції «нейрон представлення».
     *
     * @param neuron нейрон, над яким виконується операція.
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronPresentation(
            Neuron neuron,
            double x,
            double y,
            double rotationDegrees,
            boolean directionReversed
    ) {
        if (neuron == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
     * Повертає результат операції «нейрон».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron neuron() {
        return neuron;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return числове значення, визначене методом.
     */
    public double x() {
        return x;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return числове значення, визначене методом.
     */
    public double y() {
        return y;
    }

    /**
     * Повертає результат операції «обертання градуси».
     *
     * @return числове значення, визначене методом.
     */
    public double rotationDegrees() {
        return rotationDegrees;
    }

    /**
     * Повертає результат операції «напрямок».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean directionReversed() {
        return directionReversed;
    }

    /**
     * Задає або оновлює значення, повʼязані з «положення».
     *
     * @param x координата по горизонталі.
     *
     * @param y координата по вертикалі.
     */
    public void setPosition(
            double x,
            double y
    ) {
        this.x = x;
        this.y = y;
    }

    /**
     * Переміщує обʼєкт «за» відповідно до переданого зміщення.
     *
     * @param dx зміщення по горизонталі.
     *
     * @param dy зміщення по вертикалі.
     */
    public void moveBy(
            double dx,
            double dy
    ) {
        x += dx;
        y += dy;
    }

    /**
     * Задає або оновлює значення, повʼязані з «обертання градуси».
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     */
    public void setRotationDegrees(
            double rotationDegrees
    ) {
        this.rotationDegrees = rotationDegrees;
    }

    /**
     * Задає або оновлює значення, повʼязані з «напрямок».
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     */
    public void setDirectionReversed(
            boolean directionReversed
    ) {
        this.directionReversed = directionReversed;
    }

    /**
     * Перемикає стан «напрямок».
     */
    public void toggleDirection() {
        directionReversed = !directionReversed;
    }
}
