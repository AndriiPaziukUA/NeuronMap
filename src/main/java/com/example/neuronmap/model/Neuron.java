package com.example.neuronmap.model;

/**
 * Зберігає семантичний стан нейрона: тип, назву, поточну активацію та налаштування сигналу.
 * Числові параметри й режим активації згруповані в {@link NeuronParameters}, щоб оновлювати їх узгоджено.
 */
public final class Neuron {

    /** Максимальна кількість символів у користувацькій назві нейрона. */
    public static final int MAX_NAME_LENGTH = 30;

    private final String id;
    private NeuronType type;
    private int activation;
    private String name;
    private NeuronParameters parameters;

    /**
     * Створює нейрон із типовими параметрами сигналу, одним активним тактом і затримкою в один такт.
     * Типовий спосіб активації — за силою сигналів.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @throws IllegalArgumentException якщо ідентифікатор порожній або тип не задано.
     */
    public Neuron(String id, NeuronType type, int activation) {
        this(id, type, activation, new NeuronParameters(1, 1, 1, 1));
    }

    /**
     * Створює нейрон зі вказаною силою сигналу та порогом активації, зберігаючи сумісність зі старими викликами.
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
     * Створює нейрон із параметрами сили сигналу, порога активації та тривалості активності.
     * Затримка для сумісності зі старими викликами дорівнює одному такту.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     * @param activeTicks кількість тактів, протягом яких нейрон передає сигнал.
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
     * Створює нейрон з усіма параметрами сигналу, включно із затримкою перед першим вихідним сигналом.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початкове значення активації.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold поріг суми вхідних сигналів для активації нейрона.
     * @param activeTicks кількість тактів, протягом яких нейрон передає сигнал.
     * @param delayTicks кількість тактів затримки перед першим вихідним сигналом.
     * @throws IllegalArgumentException якщо ідентифікатор порожній, тип не задано або параметри некоректні.
     */
    public Neuron(
            String id,
            NeuronType type,
            int activation,
            int signalStrength,
            int activationThreshold,
            int activeTicks,
            int delayTicks
    ) {
        this(id, type, activation,
                new NeuronParameters(signalStrength, activationThreshold, activeTicks, delayTicks));
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
        this.name = null;
    }

    /** Повертає унікальний ідентифікатор нейрона.
     * @return ідентифікатор нейрона.
     */
    public String id() { return id; }

    /** Повертає користувацьку назву нейрона.
     * @return назва або {@code null}, якщо її не задано.
     */
    public String name() { return name; }

    /**
     * Установлює назву нейрона; порожній рядок нормалізується до {@code null}.
     *
     * @param name назва довжиною не більш як 30 символів або {@code null} для очищення.
     * @throws IllegalArgumentException якщо назва перевищує 30 символів.
     */
    public void setName(String name) {
        if (name != null && name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Neuron name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        this.name = name == null || name.isEmpty() ? null : name;
    }

    /** Повертає тип нейрона, що визначає знак його вихідного сигналу.
     * @return тип нейрона.
     */
    public NeuronType type() { return type; }

    /**
     * Установлює тип нейрона.
     *
     * @param type новий тип, який не може бути {@code null}.
     * @throws IllegalArgumentException якщо тип не задано.
     */
    public void setType(NeuronType type) {
        if (type == null) {
            throw new IllegalArgumentException("Neuron type must not be null.");
        }
        this.type = type;
    }

    /** Повертає поточне значення активації нейрона.
     * @return значення активації.
     */
    public int activation() { return activation; }

    /** Установлює поточне значення активації.
     * @param activation нове значення активації.
     */
    public void setActivation(int activation) { this.activation = activation; }

    /**
     * Повертає незмінний об'єкт параметрів нейрона, включно зі способом перевірки порога.
     *
     * @return усі параметри сигналу та режим активації.
     */
    public NeuronParameters parameters() { return parameters; }

    /**
     * Замінює всі параметри нейрона однією перевіреною операцією.
     *
     * @param parameters повний набір нових параметрів.
     * @throws IllegalArgumentException якщо параметри не задано.
     */
    public void setParameters(NeuronParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }
        this.parameters = parameters;
    }

    /** Повертає режим перевірки порога активації.
     * @return спосіб інтерпретації порога.
     */
    public NeuronActivationMode activationMode() { return parameters.activationMode(); }

    /**
     * Змінює спосіб активації, зберігаючи інші параметри й перевіряючи поріг для нового режиму.
     *
     * @param activationMode новий спосіб активації.
     * @throws IllegalArgumentException якщо режим не задано або поточне значення порога не підходить йому.
     */
    public void setActivationMode(NeuronActivationMode activationMode) {
        parameters = new NeuronParameters(parameters.signalStrength(), parameters.activationThreshold(),
                parameters.activeTicks(), parameters.delayTicks(), activationMode);
    }

    /** Повертає налаштовану силу вихідного сигналу нейрона.
     * @return додатна сила сигналу.
     */
    public int signalStrength() { return parameters.signalStrength(); }

    /**
     * Установлює силу сигналу, зберігаючи решту параметрів, зокрема режим активації.
     *
     * @param signalStrength нова додатна сила сигналу.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setSignalStrength(int signalStrength) {
        parameters = new NeuronParameters(signalStrength, parameters.activationThreshold(),
                parameters.activeTicks(), parameters.delayTicks(), parameters.activationMode());
    }

    /** Повертає поріг сили або відсоток вхідних сигналів.
     * @return налаштований поріг активації.
     */
    public int activationThreshold() { return parameters.activationThreshold(); }

    /**
     * Установлює поріг активації, зберігаючи решту параметрів та поточний режим.
     *
     * @param activationThreshold новий поріг сили або відсоток у межах 0–100 для режиму кількості.
     * @throws IllegalArgumentException якщо поріг не відповідає правилам поточного режиму.
     */
    public void setActivationThreshold(int activationThreshold) {
        parameters = new NeuronParameters(parameters.signalStrength(), activationThreshold,
                parameters.activeTicks(), parameters.delayTicks(), parameters.activationMode());
    }

    /** Повертає кількість активних тактів передавання сигналу.
     * @return кількість активних тактів.
     */
    public int activeTicks() { return parameters.activeTicks(); }

    /**
     * Установлює тривалість передавання сигналу, зберігаючи решту параметрів.
     *
     * @param activeTicks додатна кількість активних тактів.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setActiveTicks(int activeTicks) {
        parameters = new NeuronParameters(parameters.signalStrength(), parameters.activationThreshold(),
                activeTicks, parameters.delayTicks(), parameters.activationMode());
    }

    /** Повертає налаштовану затримку перед першим вихідним сигналом.
     * @return кількість тактів затримки.
     */
    public int delayTicks() { return parameters.delayTicks(); }

    /**
     * Установлює затримку перед першим вихідним сигналом, зберігаючи решту параметрів.
     *
     * @param delayTicks додатна кількість тактів затримки.
     * @throws IllegalArgumentException якщо затримка менша за один такт.
     */
    public void setDelayTicks(int delayTicks) {
        parameters = new NeuronParameters(parameters.signalStrength(), parameters.activationThreshold(),
                parameters.activeTicks(), delayTicks, parameters.activationMode());
    }

    /**
     * Повертає вагу сигналу з урахуванням типу нейрона та налаштованої сили.
     *
     * @return знакова вага сигналу.
     */
    public int signalWeight() {
        return type == NeuronType.EXCITATORY ? parameters.signalStrength() : -parameters.signalStrength();
    }
}
