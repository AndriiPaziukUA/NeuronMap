package com.example.neuronmap.model;

/**
 * Зберігає семантичний стан нейрона: тип, силу сигналу та поріг активації.
 */
public final class Neuron {

    private final String id;
    private NeuronType type;
    private int activation;
    private int signalStrength;
    private int activationThreshold;

    /**
     * Створює екземпляр Neuron та зберігає передані залежності, потрібні для його роботи.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення «activation», яке використовується в цьому методі.
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
     * Створює екземпляр Neuron та зберігає передані залежності, потрібні для його роботи.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param type тип нейрона або елемента.
     * @param activation значення «activation», яке використовується в цьому методі.
     * @param signalStrength сила сигналу нейрона.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     */
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

    /**
     * Повертає унікальний ідентифікатор нейрона.
     *
     * @return унікальний ідентифікатор нейрона.
     */
    public String id() {
        return id;
    }

    /**
     * Повертає тип нейрона, що визначає знак базової ваги сигналу.
     *
     * @return тип нейрона, що визначає знак базової ваги сигналу.
     */
    public NeuronType type() {
        return type;
    }

    /**
     * Установлює type для поточного об’єкта.
     *
     * @param type тип нейрона або елемента.
     */
    public void setType(NeuronType type) {
        if (type == null) {

            throw new IllegalArgumentException(
                    "Neuron type must not be null."
            );
        }

        this.type = type;
    }

    /**
     * Повертає поточне значення активації нейрона.
     *
     * @return поточне значення активації нейрона.
     */
    public int activation() {
        return activation;
    }

    /**
     * Установлює activation для поточного об’єкта.
     *
     * @param activation значення «activation», яке використовується в цьому методі.
     */
    public void setActivation(int activation) {
        this.activation = activation;
    }

    /**
     * Повертає налаштовану силу сигналу нейрона.
     *
     * @return налаштовану силу сигналу нейрона.
     */
    public int signalStrength() {
        return signalStrength;
    }

    /**
     * Установлює signal strength для поточного об’єкта.
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
     * Повертає поріг суми вхідних сигналів, необхідний для активації нейрона.
     *
     * @return поріг суми вхідних сигналів, необхідний для активації нейрона.
     */
    public int activationThreshold() {
        return activationThreshold;
    }

    /**
     * Установлює activation threshold для поточного об’єкта.
     *
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
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
 * Повертає вагу сигналу з урахуванням типу нейрона та його налаштованої сили сигналу.
 *
 * @return вагу сигналу з урахуванням типу нейрона та його налаштованої сили сигналу.
 */
public int signalWeight() {
        return type == NeuronType.EXCITATORY
                ? signalStrength
                : -signalStrength;
    }

    /**
     * Відхиляє нульове або від’ємне значення параметра, який має бути додатним.
     *
     * @param value значення, яке потрібно зберегти або перевірити.
     * @param name назва, яку потрібно перевірити або зберегти.
     */
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
