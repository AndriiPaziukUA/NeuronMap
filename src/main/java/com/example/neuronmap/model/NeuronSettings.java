package com.example.neuronmap.model;


/**
 * Містить назву та параметри сигналу, які можуть спільно використовувати нейрон і структура.
 * Об'єкт змінюється централізовано, тому всі його користувачі бачать однакові актуальні значення.
 */
public final class NeuronSettings {

    /** Максимальна кількість кодових точок у назві нейрона або структури. */
    public static final int MAX_NAME_LENGTH = 30;

    private String name;
    private NeuronParameters parameters;

    /** Створює налаштування з типовими параметрами й без заданої назви. */
    public NeuronSettings() {
        this(null, NeuronParameters.DEFAULT);
    }

    /**
     * Створює налаштування зі вказаною назвою та набором параметрів сигналу.
     *
     * @param name назва або {@code null}, якщо назву не задано.
     * @param parameters перевірені параметри сигналу.
     * @throws IllegalArgumentException якщо назва задовга або параметри не задано.
     */
    public NeuronSettings(String name, NeuronParameters parameters) {
        setName(name);
        setParameters(parameters);
    }

    /** Повертає назву, спільну для всіх нейронів, що посилаються на ці налаштування.
     * @return назва або {@code null}, якщо її не задано.
     */
    public String name() {
        return name;
    }

    /**
     * Змінює спільну назву та нормалізує порожній рядок до {@code null}.
     *
     * @param name назва довжиною не більш як {@link #MAX_NAME_LENGTH} кодових точок або {@code null}.
     * @throws IllegalArgumentException якщо назва перевищує дозволену довжину.
     */
    public void setName(String name) {
        if (name != null && name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Neuron name must not exceed " + MAX_NAME_LENGTH + " characters.");
        }
        this.name = name == null || name.isEmpty() ? null : name;
    }

    /** Повертає незмінний набір параметрів, спільний для власника й усіх його учасників.
     * @return поточні параметри сигналу.
     */
    public NeuronParameters parameters() {
        return parameters;
    }

    /**
     * Замінює всі параметри сигналу однією перевіреною операцією.
     *
     * @param parameters повний набір параметрів.
     * @throws IllegalArgumentException якщо параметри не задано.
     */
    public void setParameters(NeuronParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Neuron parameters must not be null.");
        }
        this.parameters = parameters;
    }

    /**
     * Створює незалежну копію поточних налаштувань для моменту виходу нейрона зі структури.
     * Незмінний запис параметрів копіюється, щоб від'єднаний об'єкт мав власний набір значень.
     *
     * @return новий об'єкт налаштувань з такими самими поточними значеннями.
     */
    public NeuronSettings copy() {
        NeuronParameters current = parameters;
        NeuronParameters copiedParameters = new NeuronParameters(
                current.signalStrength(),
                current.activationThreshold(),
                current.activeTicks(),
                current.delayTicks(),
                current.activationMode()
        );
        return new NeuronSettings(name, copiedParameters);
    }
}
