package com.example.neuronmap.model;

/**
 * Зберігає семантичний стан нейрона: тип, поточну активацію та налаштування сигналу.
 * Сила сигналу, поріг активації й тривалість активності зберігаються разом у {@link NeuronParameters}.
 */
public final class Neuron {

    private final String id;
    private NeuronType type;
    private int activation;
    private NeuronParameters parameters;

    /**
     * Створює нейрон із типовими параметрами сигналу та одним активним тактом.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @throws IllegalArgumentException якщо ідентифікатор порожній або тип не задано.
     */
    public Neuron(String id, NeuronType type, int activation) {
        this(id, type, activation, new NeuronParameters(1, 1, 1));
    }

    /**
     * Створює нейрон із указаною силою сигналу та порогом активації, зберігаючи сумісність зі старими викликами.
     * Для створеного нейрона тривалість активності дорівнює одному такту.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     * @throws IllegalArgumentException якщо ідентифікатор порожній, тип не задано або параметри некоректні.
     */
    public Neuron(
            String id,
            NeuronType type,
            int activation,
            int signalStrength,
            int activationThreshold
    ) {
        this(id, type, activation, new NeuronParameters(signalStrength, activationThreshold));
    }

    /**
     * Створює нейрон з усіма параметрами сигналу, включно з тривалістю активності.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     * @param activeTicks кількість тактів, протягом яких нейрон випускає сигнал.
     * @throws IllegalArgumentException якщо ідентифікатор порожній, тип не задано або параметри некоректні.
     */
    public Neuron(
            String id,
            NeuronType type,
            int activation,
            int signalStrength,
            int activationThreshold,
            int activeTicks
    ) {
        this(id, type, activation,
                new NeuronParameters(signalStrength, activationThreshold, activeTicks));
    }

    /**
     * Створює нейрон зі спільним незмінним об'єктом його параметрів.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @param parameters перевірені параметри сигналу нейрона.
     * @throws IllegalArgumentException якщо ідентифікатор порожній або тип чи параметри не задано.
     */
    public Neuron(String id, NeuronType type, int activation, NeuronParameters parameters) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Neuron id must not be blank.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Neuron type must not be null.");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }

        this.id = id;
        this.type = type;
        this.activation = activation;
        this.parameters = parameters;
    }

    /**
     * Повертає унікальний ідентифікатор нейрона.
     *
     * @return ідентифікатор нейрона.
     */
    public String id() {
        return id;
    }

    /**
     * Повертає тип нейрона, що визначає знак вихідного сигналу.
     *
     * @return тип нейрона.
     */
    public NeuronType type() {
        return type;
    }

    /**
     * Установлює тип нейрона.
     *
     * @param type новий тип нейрона.
     * @throws IllegalArgumentException якщо тип не задано.
     */
    public void setType(NeuronType type) {
        if (type == null) {
            throw new IllegalArgumentException("Neuron type must not be null.");
        }
        this.type = type;
    }

    /**
     * Повертає поточне значення активації нейрона.
     *
     * @return поточне значення активації.
     */
    public int activation() {
        return activation;
    }

    /**
     * Установлює поточне значення активації нейрона.
     *
     * @param activation нове значення активації.
     */
    public void setActivation(int activation) {
        this.activation = activation;
    }

    /**
     * Повертає незмінний об'єкт налаштувань сигналу нейрона.
     *
     * @return параметри сили сигналу, порога активації та активних тактів.
     */
    public NeuronParameters parameters() {
        return parameters;
    }

    /**
     * Замінює всі параметри нейрона однією перевіреною операцією.
     *
     * @param parameters нові параметри нейрона.
     * @throws IllegalArgumentException якщо параметри не задано.
     */
    public void setParameters(NeuronParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }
        this.parameters = parameters;
    }

    /**
     * Повертає налаштовану силу вихідного сигналу нейрона.
     *
     * @return сила сигналу.
     */
    public int signalStrength() {
        return parameters.signalStrength();
    }

    /**
     * Установлює силу сигналу, зберігаючи поріг активації та тривалість активності.
     *
     * @param signalStrength нова сила сигналу.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setSignalStrength(int signalStrength) {
        parameters = new NeuronParameters(
                signalStrength,
                parameters.activationThreshold(),
                parameters.activeTicks()
        );
    }

    /**
     * Повертає поріг суми вхідних сигналів, необхідний для активації нейрона.
     *
     * @return поріг активації.
     */
    public int activationThreshold() {
        return parameters.activationThreshold();
    }

    /**
     * Установлює поріг активації, зберігаючи силу сигналу та тривалість активності.
     *
     * @param activationThreshold новий поріг активації.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setActivationThreshold(int activationThreshold) {
        parameters = new NeuronParameters(
                parameters.signalStrength(),
                activationThreshold,
                parameters.activeTicks()
        );
    }

    /**
     * Повертає налаштовану кількість тактів, протягом яких нейрон випускає по одному сигналу.
     *
     * @return кількість активних тактів після активації.
     */
    public int activeTicks() {
        return parameters.activeTicks();
    }

    /**
     * Установлює тривалість активності, зберігаючи силу сигналу та поріг активації.
     *
     * @param activeTicks кількість тактів, протягом яких нейрон випускає сигнали.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setActiveTicks(int activeTicks) {
        parameters = new NeuronParameters(
                parameters.signalStrength(),
                parameters.activationThreshold(),
                activeTicks
        );
    }

    /**
     * Повертає вагу сигналу з урахуванням типу нейрона та налаштованої сили.
     *
     * @return додатна вага для збуджувального нейрона або від'ємна для гальмівного.
     */
    public int signalWeight() {
        return type == NeuronType.EXCITATORY
                ? parameters.signalStrength()
                : -parameters.signalStrength();
    }
}
