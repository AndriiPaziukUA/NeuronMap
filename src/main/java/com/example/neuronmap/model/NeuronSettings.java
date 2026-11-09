package com.example.neuronmap.model;

/**
 * Містить назву, спільні параметри сигналу та режими поведінки дерева нейронів структури.
 * Персональна адаптивна сила кожного учасника зберігається в самому {@link Neuron}, а тип сигналу лишається спільним.
 */
public final class NeuronSettings {

    /** Максимальна кількість кодових точок у назві нейрона або структури. */
    public static final int MAX_NAME_LENGTH = 30;

    private String name;
    private NeuronParameters parameters;
    private NeuronType memberType;
    private boolean directionReversed;
    private boolean strongestNeuronOnly;
    private StructureNeuronCreationMode neuronCreationMode = StructureNeuronCreationMode.NEURON_COUNT;
    /** Змінюється лише під час переходу зі статичного режиму до будь-якого адаптивного. */
    private long adaptiveSignalRevision;

    /** Створює налаштування з типовими параметрами й без заданої назви. */
    public NeuronSettings() {
        this(null, NeuronParameters.DEFAULT);
    }

    /** Створює налаштування зі вказаною назвою та набором параметрів сигналу. */
    public NeuronSettings(String name, NeuronParameters parameters) {
        setName(name);
        setParameters(parameters);
    }

    /** Повертає спільну назву всіх нейронів, що посилаються на ці налаштування. */
    public String name() { return name; }

    /** Змінює спільну назву та нормалізує порожній рядок до {@code null}. */
    public void setName(String name) {
        if (name != null && name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Neuron name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        this.name = name == null || name.isEmpty() ? null : name;
    }

    /** Повертає базовий набір параметрів, спільний для власника й усіх його учасників. */
    public NeuronParameters parameters() { return parameters; }

    /**
     * Замінює спільні параметри. Перехід зі статичного типу до адаптивного збільшує ревізію,
     * щоб кожен учасник ініціалізував персональну силу актуальним спільним значенням.
     */
    public void setParameters(NeuronParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }
        if (this.parameters != null
                && this.parameters.signalType() == SignalType.STATIC
                && parameters.signalType() != SignalType.STATIC) {
            adaptiveSignalRevision++;
        }
        this.parameters = parameters;
    }

    /** Повертає спільний тип звичайних учасників дерева; за відсутності заданого типу використовує збуджувальний. */
    public NeuronType memberType() { return memberType == null ? NeuronType.EXCITATORY : memberType; }

    /** Повертає явно заданий спільний тип без підстановки типового значення. */
    NeuronType memberTypeOrNull() { return memberType; }

    /** Ініціалізує спільний тип лише один раз, коли вперше під'єднується наявний учасник. */
    void initializeMemberType(NeuronType type) {
        if (memberType == null && type != null && type != NeuronType.STRUCTURE) {
            memberType = type;
        }
    }

    /** Змінює єдиний тип усіх звичайних нейронів у спільному дереві структури. */
    public void setMemberType(NeuronType type) {
        if (type == null || type == NeuronType.STRUCTURE) {
            throw new IllegalArgumentException("Structure member type must be excitatory or inhibitory.");
        }
        memberType = type;
    }

    /** Повертає спільний напрямок дерева структурних нейронів. */
    public boolean directionReversed() { return directionReversed; }

    /** Установлює єдиний напрямок структури та її учасників. */
    public void setDirectionReversed(boolean directionReversed) { this.directionReversed = directionReversed; }

    /** Показує, чи мають звичайні нейрони структурного дерева конкурувати за найбільшим вхідним сигналом. */
    public boolean strongestNeuronOnly() { return strongestNeuronOnly; }

    /** Умикає або вимикає конкуренцію учасників за найбільшим вхідним сигналом. */
    public void setStrongestNeuronOnly(boolean strongestNeuronOnly) { this.strongestNeuronOnly = strongestNeuronOnly; }

    /** Повертає умову, за якою структура створює новий спільний нейрон для активованої групи. */
    public StructureNeuronCreationMode neuronCreationMode() { return neuronCreationMode; }

    /** Змінює умову створення спільного нейрона для всього дерева структури. */
    public void setNeuronCreationMode(StructureNeuronCreationMode neuronCreationMode) {
        if (neuronCreationMode == null) {
            throw new IllegalArgumentException("Neuron creation mode must not be null.");
        }
        this.neuronCreationMode = neuronCreationMode;
    }

    /** Повертає ревізію, за якою учасники визначають потребу ініціалізувати персональну силу сигналу. */
    long adaptiveSignalRevision() { return adaptiveSignalRevision; }

    /** Створює незалежну копію спільних налаштувань разом із поточною ревізією адаптивної сили. */
    public NeuronSettings copy() {
        NeuronParameters current = parameters;
        NeuronSettings copy = new NeuronSettings(name, new NeuronParameters(
                current.signalStrength(), current.activationThreshold(), current.activeTicks(),
                current.delayTicks(), current.activationMode(), current.signalType()));
        copy.memberType = memberType;
        copy.directionReversed = directionReversed;
        copy.strongestNeuronOnly = strongestNeuronOnly;
        copy.neuronCreationMode = neuronCreationMode;
        copy.adaptiveSignalRevision = adaptiveSignalRevision;
        return copy;
    }
}
