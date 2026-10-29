package com.example.neuronmap.model;

/**
 * Містить назву, параметри сигналу та спільні характеристики дерева нейронів структури.
 * Об'єкт змінюється централізовано, тому всі нейрони, які посилаються на нього, бачать актуальні значення.
 */
public final class NeuronSettings {

    /** Максимальна кількість кодових точок у назві нейрона або структури. */
    public static final int MAX_NAME_LENGTH = 30;

    private String name;
    private NeuronParameters parameters;
    /** Спільний тип звичайних нейронів дерева; null означає, що тип ще не визначено членством. */
    private NeuronType memberType;
    /** Спільний напрямок нейрона, який представляє структуру, та всіх її учасників. */
    private boolean directionReversed;

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
     * Повертає спільний тип звичайних нейронів дерева структури; за відсутності заданого типу використовує збуджувальний.
     *
     * @return тип учасників, який спільно визначає власник налаштувань.
     */
    public NeuronType memberType() {
        return memberType == null ? NeuronType.EXCITATORY : memberType;
    }

    /**
     * Повертає явно заданий спільний тип без підстановки типового значення.
     *
     * @return заданий тип учасників або {@code null}, якщо членство ще не визначило його.
     */
    NeuronType memberTypeOrNull() {
        return memberType;
    }

    /**
     * Ініціалізує спільний тип лише один раз, коли вперше під'єднується наявний учасник.
     * Це зберігає тип під час відновлення структури зі збереженої карти.
     *
     * @param type тип першого звичайного учасника.
     */
    void initializeMemberType(NeuronType type) {
        if (memberType == null && type != null && type != NeuronType.STRUCTURE) {
            memberType = type;
        }
    }

    /**
     * Змінює єдиний тип усіх звичайних нейронів у спільному дереві структури.
     *
     * @param type новий тип учасників; тип контейнера {@link NeuronType#STRUCTURE} не дозволений.
     * @throws IllegalArgumentException якщо тип не задано або він позначає структуру.
     */
    public void setMemberType(NeuronType type) {
        if (type == null || type == NeuronType.STRUCTURE) {
            throw new IllegalArgumentException("Structure member type must be excitatory or inhibitory.");
        }
        memberType = type;
    }

    /** Повертає спільний напрямок дерева структурних нейронів.
     * @return {@code true}, якщо напрямок розвернуто.
     */
    public boolean directionReversed() {
        return directionReversed;
    }

    /**
     * Установлює єдиний напрямок структури та її учасників.
     *
     * @param directionReversed {@code true}, якщо напрямок потрібно розвернути.
     */
    public void setDirectionReversed(boolean directionReversed) {
        this.directionReversed = directionReversed;
    }

    /**
     * Створює незалежну копію поточних налаштувань для моменту виходу нейрона зі структури.
     * Копіюються також спільний тип учасників і напрямок, щоб від'єднана гілка зберігала свій стан.
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
        NeuronSettings copy = new NeuronSettings(name, copiedParameters);
        copy.memberType = memberType;
        copy.directionReversed = directionReversed;
        return copy;
    }
}
