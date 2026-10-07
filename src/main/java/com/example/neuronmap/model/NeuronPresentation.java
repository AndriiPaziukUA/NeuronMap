package com.example.neuronmap.model;

/**
 * Visual state of a neuron on the board.
 *
 * The semantic neuron intentionally does not contain coordinates, rotation
 * or JavaFX state. Direction is presentation state because it changes the
 * side on which the input/output ports are rendered.
 */
public final class NeuronPresentation {

    private final Neuron neuron;
    private double x;
    private double y;
    private double rotationDegrees;
    private boolean directionReversed;

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

    public Neuron neuron() {
        return neuron;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double rotationDegrees() {
        return rotationDegrees;
    }

    public boolean directionReversed() {
        return directionReversed;
    }

    public void setPosition(
            double x,
            double y
    ) {
        this.x = x;
        this.y = y;
    }

    public void moveBy(
            double dx,
            double dy
    ) {
        x += dx;
        y += dy;
    }

    public void setRotationDegrees(
            double rotationDegrees
    ) {
        this.rotationDegrees = rotationDegrees;
    }

    public void setDirectionReversed(
            boolean directionReversed
    ) {
        this.directionReversed = directionReversed;
    }

    public void toggleDirection() {
        directionReversed = !directionReversed;
    }
}
