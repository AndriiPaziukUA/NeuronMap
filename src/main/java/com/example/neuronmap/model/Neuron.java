package com.example.neuronmap.model;

/**
 * Зберігає семантичний стан нейрона та делегує спільні параметри власнику структури.
 * В адаптивних режимах звичайний нейрон додатково зберігає власну силу сигналу.
 */
public final class Neuron {

    /** Максимальна кількість символів у користувацькій назві нейрона. */
    public static final int MAX_NAME_LENGTH = NeuronSettings.MAX_NAME_LENGTH;

    private final String id;
    private NeuronType type;
    private int activation;
    private NeuronSettings settings;
    private int adaptiveSignalStrength;
    private long adaptiveSignalRevision;

    /**
     * Створює нейрон із типовими параметрами та статичним типом сигналу.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початковий стан активації.
     * @throws IllegalArgumentException якщо ідентифікатор порожній або тип не задано.
     */
    public Neuron(String id, NeuronType type, int activation) {
        this(id, type, activation, NeuronParameters.DEFAULT);
    }

    /**
     * Створює нейрон із силою сигналу та порогом активації.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початковий стан активації.
     * @param signalStrength додатна сила вихідного сигналу.
     * @param activationThreshold поріг активації.
     */
    public Neuron(String id, NeuronType type, int activation, int signalStrength, int activationThreshold) {
        this(id, type, activation, new NeuronParameters(signalStrength, activationThreshold));
    }

    /**
     * Створює нейрон із силою сигналу, порогом і тривалістю активності.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початковий стан активації.
     * @param signalStrength додатна сила вихідного сигналу.
     * @param activationThreshold поріг активації.
     * @param activeTicks кількість активних тактів.
     */
    public Neuron(String id, NeuronType type, int activation, int signalStrength,
                  int activationThreshold, int activeTicks) {
        this(id, type, activation, new NeuronParameters(signalStrength, activationThreshold, activeTicks));
    }

    /**
     * Створює нейрон із повним набором параметрів сигналу.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початковий стан активації.
     * @param signalStrength додатна сила вихідного сигналу.
     * @param activationThreshold поріг активації.
     * @param activeTicks кількість активних тактів.
     * @param delayTicks затримка перед першим вихідним сигналом.
     */
    public Neuron(String id, NeuronType type, int activation, int signalStrength,
                  int activationThreshold, int activeTicks, int delayTicks) {
        this(id, type, activation,
                new NeuronParameters(signalStrength, activationThreshold, activeTicks, delayTicks));
    }

    /**
     * Створює нейрон із заданим набором параметрів та ініціалізує його персональний стан.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @param activation початковий стан активації.
     * @param parameters перевірений набір параметрів сигналу.
     * @throws IllegalArgumentException якщо ідентифікатор порожній або обов'язкові значення не задано.
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
        settings = new NeuronSettings(null, parameters);
        adaptiveSignalStrength = parameters.signalStrength();
        adaptiveSignalRevision = settings.adaptiveSignalRevision();
    }

    /** Повертає унікальний ідентифікатор нейрона. @return ідентифікатор нейрона. */
    public String id() { return id; }

    /** Повертає спільну назву нейрона або його структури-власника. @return назва або {@code null}. */
    public String name() { return settings.name(); }

    /**
     * Установлює назву нейрона або структури-власника.
     *
     * @param name нова назва або {@code null} для очищення.
     */
    public void setName(String name) { settings.setName(name); }

    /** Повертає фактичний тип з урахуванням спільного типу дерева структури. @return тип нейрона. */
    public NeuronType type() {
        if (type == NeuronType.STRUCTURE || settings.memberTypeOrNull() == null) {
            return type;
        }
        return settings.memberType();
    }

    /**
     * Змінює тип нейрона або спільний тип звичайних учасників структури.
     *
     * @param type новий тип нейрона.
     * @throws IllegalArgumentException якщо тип не задано.
     */
    public void setType(NeuronType type) {
        if (type == null) {
            throw new IllegalArgumentException("Neuron type must not be null.");
        }
        if (type != NeuronType.STRUCTURE && this.type != NeuronType.STRUCTURE
                && settings.memberTypeOrNull() != null) {
            settings.setMemberType(type);
            return;
        }
        this.type = type;
    }

    /** Повертає стан активації. @return поточна активація. */
    public int activation() { return activation; }

    /** Установлює стан активації. @param activation нове значення активації. */
    public void setActivation(int activation) { this.activation = activation; }

    /**
     * Повертає параметри, у яких сила адаптивного режиму є персональною для звичайного нейрона.
     *
     * @return параметри сигналу з ефективною силою.
     */
    public NeuronParameters parameters() {
        NeuronParameters shared = settings.parameters();
        if (type != NeuronType.STRUCTURE && shared.signalType() != SignalType.STATIC) {
            return shared.withSignalStrength(signalStrength());
        }
        return shared;
    }

    /**
     * Замінює параметри налаштувань. Для учасника структури в уже активному адаптивному режимі
     * базова сила лишається спільною, а передана сила оновлює лише персональне значення цього учасника.
     * Перехід до статичного режиму, навпаки, переносить вибрану силу до спільних налаштувань.
     *
     * @param parameters новий набір параметрів.
     * @throws IllegalArgumentException якщо параметри не задано.
     */
    public void setParameters(NeuronParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }
        NeuronParameters current = settings.parameters();
        boolean preserveSharedAdaptiveStrength = type != NeuronType.STRUCTURE
                && settings.memberTypeOrNull() != null
                && current.signalType() != SignalType.STATIC
                && parameters.signalType() != SignalType.STATIC;
        NeuronParameters sharedParameters = preserveSharedAdaptiveStrength
                ? parameters.withSignalStrength(current.signalStrength())
                : parameters;
        settings.setParameters(sharedParameters);
        if (type != NeuronType.STRUCTURE && parameters.signalType() != SignalType.STATIC) {
            adaptiveSignalStrength = parameters.signalStrength();
            adaptiveSignalRevision = settings.adaptiveSignalRevision();
        }
    }

    /** Повертає режим перевірки порога активації. @return режим активації. */
    public NeuronActivationMode activationMode() { return settings.parameters().activationMode(); }

    /** Повертає спільний тип сигналу. @return тип сигналу. */
    public SignalType signalType() { return settings.parameters().signalType(); }

    /**
     * Змінює спільний тип сигналу структури або тип окремого нейрона.
     * Під час переходу адаптивного учасника до статичного режиму його ефективна сила стає спільною.
     *
     * @param signalType новий тип сигналу.
     * @throws IllegalArgumentException якщо тип сигналу не задано.
     */
    public void setSignalType(SignalType signalType) {
        if (signalType == null) {
            throw new IllegalArgumentException("Signal type must not be null.");
        }
        NeuronParameters current = settings.parameters();
        int sharedStrength = current.signalStrength();
        if (type != NeuronType.STRUCTURE
                && current.signalType() != SignalType.STATIC
                && signalType == SignalType.STATIC) {
            sharedStrength = signalStrength();
        }
        settings.setParameters(current.withSignalStrength(sharedStrength).withSignalType(signalType));
    }

    /** Повертає режим конкуренції учасників структури. @return {@code true}, якщо конкуренцію ввімкнено. */
    public boolean strongestNeuronOnly() { return settings.strongestNeuronOnly(); }

    /** Повертає спільну умову створення нейрона. @return режим створення. */
    public StructureNeuronCreationMode neuronCreationMode() { return settings.neuronCreationMode(); }

    /**
     * Змінює режим активації, не скидаючи персональну адаптивну силу.
     *
     * @param activationMode новий режим активації.
     */
    public void setActivationMode(NeuronActivationMode activationMode) {
        NeuronParameters current = settings.parameters();
        settings.setParameters(new NeuronParameters(current.signalStrength(), current.activationThreshold(),
                current.activeTicks(), current.delayTicks(), activationMode, current.signalType()));
    }

    /** Повертає ефективну силу, яку нейрон використає для наступного вихідного сигналу. @return сила сигналу. */
    public int signalStrength() {
        NeuronParameters current = settings.parameters();
        if (type == NeuronType.STRUCTURE || current.signalType() == SignalType.STATIC) {
            return current.signalStrength();
        }
        initializeAdaptiveSignalStrengthIfRequired();
        return adaptiveSignalStrength;
    }

    /**
     * Установлює силу сигналу; в адаптивному режимі змінює лише персональне значення нейрона.
     *
     * @param signalStrength нова додатна сила сигналу.
     * @throws IllegalArgumentException якщо значення не є додатним.
     */
    public void setSignalStrength(int signalStrength) {
        if (signalStrength <= 0) {
            throw new IllegalArgumentException("Signal strength must be greater than zero.");
        }
        if (type != NeuronType.STRUCTURE && signalType() != SignalType.STATIC) {
            initializeAdaptiveSignalStrengthIfRequired();
            adaptiveSignalStrength = signalStrength;
            return;
        }
        settings.setParameters(settings.parameters().withSignalStrength(signalStrength));
    }

    /**
     * Оновлює персональну силу лише після того, як симуляція підтвердила проходження порога активації.
     *
     * @param receivedSignalStrength фактична сума сигналу, отримана в поточному такті.
     */
    public void learnSignalStrength(int receivedSignalStrength) {
        if (receivedSignalStrength <= 0) {
            return;
        }
        switch (signalType()) {
            case STATIC -> { }
            case STRONGEST -> {
                if (receivedSignalStrength > signalStrength()) {
                    setSignalStrength(receivedSignalStrength);
                }
            }
            case RECEIVED -> setSignalStrength(receivedSignalStrength);
        }
    }

    /** Повертає поріг активації. @return сила або відсоток порога. */
    public int activationThreshold() { return settings.parameters().activationThreshold(); }

    /**
     * Установлює поріг активації, не змінюючи персональної сили сигналу.
     *
     * @param activationThreshold новий поріг активації.
     */
    public void setActivationThreshold(int activationThreshold) {
        NeuronParameters current = settings.parameters();
        settings.setParameters(new NeuronParameters(current.signalStrength(), activationThreshold,
                current.activeTicks(), current.delayTicks(), current.activationMode(), current.signalType()));
    }

    /** Повертає кількість активних тактів. @return тривалість активності. */
    public int activeTicks() { return settings.parameters().activeTicks(); }

    /**
     * Установлює тривалість активності без скидання персональної адаптивної сили.
     *
     * @param activeTicks кількість активних тактів.
     */
    public void setActiveTicks(int activeTicks) {
        NeuronParameters current = settings.parameters();
        settings.setParameters(new NeuronParameters(current.signalStrength(), current.activationThreshold(),
                activeTicks, current.delayTicks(), current.activationMode(), current.signalType()));
    }

    /** Повертає затримку перед першим вихідним сигналом. @return кількість тактів затримки. */
    public int delayTicks() { return settings.parameters().delayTicks(); }

    /**
     * Установлює затримку, не скидаючи персональної адаптивної сили.
     *
     * @param delayTicks кількість тактів затримки.
     */
    public void setDelayTicks(int delayTicks) {
        NeuronParameters current = settings.parameters();
        settings.setParameters(new NeuronParameters(current.signalStrength(), current.activationThreshold(),
                current.activeTicks(), delayTicks, current.activationMode(), current.signalType()));
    }

    /** Повертає знакову вагу сигналу. @return додатна вага для збуджувального або від'ємна для гальмівного нейрона. */
    public int signalWeight() {
        return type().isActivating() ? signalStrength() : -signalStrength();
    }

    /**
     * Прив'язує нейрон до спільних налаштувань структури, зберігаючи адаптивну силу при переході між деревами.
     *
     * @param sharedSettings спільні налаштування структури-власника.
     * @throws IllegalArgumentException якщо налаштування не задано.
     */
    void shareSettingsWith(NeuronSettings sharedSettings) {
        if (sharedSettings == null) {
            throw new IllegalArgumentException("Shared settings must not be null.");
        }
        if (settings == sharedSettings) {
            return;
        }
        if (type != NeuronType.STRUCTURE) {
            sharedSettings.initializeMemberType(type());
        }
        boolean preserveAdaptiveStrength = type != NeuronType.STRUCTURE
                && settings.parameters().signalType() != SignalType.STATIC
                && sharedSettings.parameters().signalType() != SignalType.STATIC;
        int previousStrength = preserveAdaptiveStrength ? signalStrength() : 0;
        settings = sharedSettings;
        if (preserveAdaptiveStrength) {
            adaptiveSignalStrength = previousStrength;
            adaptiveSignalRevision = sharedSettings.adaptiveSignalRevision();
        } else {
            adaptiveSignalRevision = Long.MIN_VALUE;
        }
    }

    /** Повертає об'єкт спільних налаштувань для операцій моделі. @return поточні налаштування. */
    NeuronSettings settings() { return settings; }

    /** Від'єднує нейрон від спільних налаштувань, зберігаючи його фактичну персональну силу. */
    void detachSettings() {
        NeuronType currentType = type();
        int currentStrength = type == NeuronType.STRUCTURE ? 0 : signalStrength();
        settings = settings.copy();
        if (type != NeuronType.STRUCTURE) {
            type = currentType;
            if (settings.parameters().signalType() != SignalType.STATIC) {
                adaptiveSignalStrength = currentStrength;
                adaptiveSignalRevision = settings.adaptiveSignalRevision();
            }
        }
    }

    /**
     * Ініціалізує персональну силу після переходу спільних налаштувань до нового адаптивного режиму.
     * Ревізія дає змогу виконати ініціалізацію один раз без обходу всього дерева структури.
     */
    private void initializeAdaptiveSignalStrengthIfRequired() {
        long currentRevision = settings.adaptiveSignalRevision();
        if (adaptiveSignalRevision != currentRevision) {
            adaptiveSignalStrength = settings.parameters().signalStrength();
            adaptiveSignalRevision = currentRevision;
        }
    }
}
