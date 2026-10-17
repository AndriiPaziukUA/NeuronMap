package com.example.neuronmap.model;

import java.util.Objects;

/**
 * Зберігає візуальне розташування нейрона, кут обертання й напрямок входу та виходу.
 * Координати об'єднано в {@link MapPosition}, щоб позиція була одним значенням стану.
 */
public final class NeuronPresentation {

    private final Neuron neuron;
    private MapPosition position;
    private double rotationDegrees;
    private boolean directionReversed;

    /**
     * Створює подання нейрона з указаними координатами, кутом і звичайним напрямком портів.
     *
     * @param neuron нейрон моделі, який представляє цей об'єкт.
     * @param x початкова горизонтальна координата на карті.
     * @param y початкова вертикальна координата на карті.
     * @param rotationDegrees початковий кут обертання в градусах.
     * @throws IllegalArgumentException якщо нейрон не задано.
     */
    public NeuronPresentation(
            Neuron neuron,
            double x,
            double y,
            double rotationDegrees
    ) {
        this(neuron, new MapPosition(x, y), rotationDegrees, false);
    }

    /**
     * Створює подання нейрона з повним початковим візуальним станом.
     *
     * @param neuron нейрон моделі, який представляє цей об'єкт.
     * @param x початкова горизонтальна координата на карті.
     * @param y початкова вертикальна координата на карті.
     * @param rotationDegrees початковий кут обертання в градусах.
     * @param directionReversed {@code true}, якщо напрямок портів розвернуто.
     * @throws IllegalArgumentException якщо нейрон не задано.
     */
    public NeuronPresentation(
            Neuron neuron,
            double x,
            double y,
            double rotationDegrees,
            boolean directionReversed
    ) {
        this(neuron, new MapPosition(x, y), rotationDegrees, directionReversed);
    }

    /**
     * Створює подання нейрона зі значенням позиції, представленим окремим value object.
     *
     * @param neuron нейрон моделі, який представляє цей об'єкт.
     * @param position позиція нейрона на карті.
     * @param rotationDegrees початковий кут обертання в градусах.
     * @param directionReversed {@code true}, якщо напрямок портів розвернуто.
     * @throws IllegalArgumentException якщо нейрон або позицію не задано.
     */
    public NeuronPresentation(
            Neuron neuron,
            MapPosition position,
            double rotationDegrees,
            boolean directionReversed
    ) {
        if (neuron == null) {
            throw new IllegalArgumentException("Neuron must not be null.");
        }
        if (position == null) {
            throw new IllegalArgumentException("Position must not be null.");
        }

        this.neuron = neuron;
        this.position = position;
        this.rotationDegrees = rotationDegrees;
        this.directionReversed = directionReversed;
    }

    /**
     * Повертає нейрон, візуальне подання якого описує цей об'єкт.
     *
     * @return пов'язаний нейрон моделі.
     */
    public Neuron neuron() {
        return neuron;
    }

    /**
     * Повертає позицію нейрона на карті як єдине значення.
     *
     * @return поточна позиція нейрона.
     */
    public MapPosition position() {
        return position;
    }

    /**
     * Повертає горизонтальну координату нейрона на карті.
     *
     * @return координата X.
     */
    public double x() {
        return position.x();
    }

    /**
     * Повертає вертикальну координату нейрона на карті.
     *
     * @return координата Y.
     */
    public double y() {
        return position.y();
    }

    /**
     * Повертає кут обертання нейрона в градусах.
     *
     * @return поточний кут обертання.
     */
    public double rotationDegrees() {
        return rotationDegrees;
    }

    /**
     * Повертає ознаку розвернутого напрямку портів нейрона.
     *
     * @return {@code true}, якщо напрямок розвернуто; інакше {@code false}.
     */
    public boolean directionReversed() {
        return directionReversed;
    }

    /**
     * Установлює позицію нейрона на карті.
     *
     * @param position нова позиція нейрона.
     * @throws NullPointerException якщо позицію не задано.
     */
    public void setPosition(MapPosition position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    /**
     * Установлює координати позиції; метод збережено для наявних викликів моделі та інтерфейсу.
     *
     * @param x нова горизонтальна координата.
     * @param y нова вертикальна координата.
     */
    public void setPosition(double x, double y) {
        setPosition(new MapPosition(x, y));
    }

    /**
     * Зміщує нейрон на заданий вектор у координатах карти.
     *
     * @param dx горизонтальна складова зміщення; додатне значення переміщує нейрон праворуч.
     * @param dy вертикальна складова зміщення; додатне значення переміщує нейрон униз.
     */
    public void moveBy(double dx, double dy) {
        position = new MapPosition(position.x() + dx, position.y() + dy);
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

    /**
     * Міняє місцями звичайний і розвернутий напрямки сигналу.
     */
    public void toggleDirection() {
        directionReversed = !directionReversed;
    }
}
