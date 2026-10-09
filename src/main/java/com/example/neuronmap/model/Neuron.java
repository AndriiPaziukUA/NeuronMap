package com.example.neuronmap.model;

/**
 * Зберігає змістові дані нейрона: тип, активацію, силу сигналу та поріг активації.
 */
public final class Neuron {

    private final String id;
    private NeuronType type;
    private int activation;
    private int signalStrength;
    private int activationThreshold;

    /**
     * Повертає результат операції «нейрон».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param type тип обʼєкта.
     *
     * @param activation значення активації нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає результат операції «нейрон».
     *
     * @param id ідентифікатор обʼєкта.
     *
     * @param type тип обʼєкта.
     *
     * @param activation значення активації нейрона.
     *
     * @param signalStrength сила сигналу нейрона.
     *
     * @param activationThreshold поріг активації нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Neuron(
            String id,
            NeuronType type,
            int activation,
            int signalStrength,
            int activationThreshold
    ) {
        if (id == null || id.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "Neuron id must not be blank."
            );
        }

        if (type == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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

    /**
     * Повертає незмінний ідентифікатор нейрона.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String id() {
        return id;
    }

    /**
     * Повертає поточний тип нейрона.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronType type() {
        return type;
    }

    /**
     * Змінює тип нейрона; null не допускається.
     *
     * @param type тип обʼєкта.
     */
    public void setType(NeuronType type) {
        if (type == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "Neuron type must not be null."
            );
        }

        this.type = type;
    }

    /**
     * Повертає поточне значення активації нейрона.
     *
     * @return числове значення, визначене методом.
     */
    public int activation() {
        return activation;
    }

    /**
     * Задає поточне значення активації нейрона.
     *
     * @param activation значення активації нейрона.
     */
    public void setActivation(int activation) {
        this.activation = activation;
    }

    /**
     * Повертає силу сигналу, яку випромінює нейрон.
     *
     * @return числове значення, визначене методом.
     */
    public int signalStrength() {
        return signalStrength;
    }

    /**
     * Задає силу сигналу; значення має бути додатним.
     *
     * @param signalStrength сила сигналу нейрона.
     */
    public void setSignalStrength(int signalStrength) {
        validatePositive(
                signalStrength,
                "Signal strength"
        );

        this.signalStrength = signalStrength;
    }

    /**
     * Повертає поріг, потрібний для активації нейрона.
     *
     * @return числове значення, визначене методом.
     */
    public int activationThreshold() {
        return activationThreshold;
    }

    /**
     * Задає поріг активації; значення має бути додатним.
     *
     * @param activationThreshold поріг активації нейрона.
     */
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
 * Повертає вагу сигналу: додатну для збуджувального нейрона та відʼємну для гальмівного.
 *
 * @return числове значення, визначене методом.
 */
public int signalWeight() {
        return type == NeuronType.EXCITATORY
                ? signalStrength
                : -signalStrength;
    }

    /**
     * Перевіряє, що передане число більше нуля; інакше кидає IllegalArgumentException.
     *
     * @param value значення, яке потрібно передати або зберегти.
     *
     * @param name назва або текстове імʼя обʼєкта.
     */
    private static void validatePositive(
            int value,
            String name
    ) {
        if (value <= 0) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    name + " must be greater than zero."
            );
        }
    }
}
