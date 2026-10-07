package com.example.neuronmap.model;

/**
 * Semantic data of a neuron.
 *
 * Position, rotation and JavaFX-specific state belong to NeuronPresentation.
 */
public final class Neuron {

    private final String id;
    private NeuronType type;
    private int activation;
    private int signalStrength;
    private int activationThreshold;

    public Neuron(
            String id,
            NeuronType type,
            int activation
    ) {
        this(
                id,
                type,
                activation,
                1,
                1
        );
    }

    public Neuron(
            String id,
            NeuronType type,
            int activation,
            int signalStrength,
            int activationThreshold
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Neuron id must not be blank."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Neuron type must not be null."
            );
        }

        validatePositive(
                signalStrength,
                "Signal strength"
        );

        validatePositive(
                activationThreshold,
                "Activation threshold"
        );

        this.id = id;
        this.type = type;
        this.activation = activation;
        this.signalStrength = signalStrength;
        this.activationThreshold = activationThreshold;
    }

    public String id() {
        return id;
    }

    public NeuronType type() {
        return type;
    }

    public void setType(NeuronType type) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Neuron type must not be null."
            );
        }

        this.type = type;
    }

    public int activation() {
        return activation;
    }

    public void setActivation(int activation) {
        this.activation = activation;
    }

    public int signalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        validatePositive(
                signalStrength,
                "Signal strength"
        );

        this.signalStrength = signalStrength;
    }

    public int activationThreshold() {
        return activationThreshold;
    }

    public void setActivationThreshold(
            int activationThreshold
    ) {
        validatePositive(
                activationThreshold,
                "Activation threshold"
        );

        this.activationThreshold = activationThreshold;
    }

    /**
     * Returns the signed signal contributed by this neuron.
     *
     * Excitatory neurons produce a positive signal, inhibitory neurons
     * produce a negative signal. The user-controlled signal strength
     * defines the absolute magnitude.
     */
    public int signalWeight() {
        return type == NeuronType.EXCITATORY
                ? signalStrength
                : -signalStrength;
    }

    private static void validatePositive(
            int value,
            String name
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be greater than zero."
            );
        }
    }
}
