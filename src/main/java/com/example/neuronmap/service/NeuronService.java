package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.Objects;

/**
 * Надає операції над нейронами: створення, пошук, видалення, переміщення, обертання,
 * зміну типу, напрямку й параметрів сигналу.
 */
public final class NeuronService {

    private final NeuronMapModel model;

    /**
     * Створює службу операцій над нейронами для заданої моделі карти.
     *
     * @param model модель карти нейронів, над якою виконуватимуться операції.
     * @throws NullPointerException якщо модель не задано.
     */
    public NeuronService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Повертає модель карти, якою керує ця служба.
     *
     * @return модель карти нейронів.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Повертає поточні нейрони моделі.
     *
     * @return колекція нейронів, яку надає модель; служба не створює окремої копії колекції.
     */
    public Collection<Neuron> neurons() {
        return model.neurons();
    }

    /**
     * Знаходить нейрон за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return знайдений нейрон або {@code null}, якщо ідентифікатор некоректний чи нейрона немає.
     */
    public Neuron find(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.neuron(neuronId);
    }

    /**
     * Повертає візуальне подання нейрона за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return подання нейрона або {@code null}, якщо ідентифікатор некоректний чи нейрон відсутній.
     */
    public NeuronPresentation presentation(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.presentation(neuronId);
    }

    /**
     * Створює нейрон указаного типу в заданих координатах.
     *
     * @param type тип нейрона.
     * @param x початкова горизонтальна координата нейрона на карті.
     * @param y початкова вертикальна координата нейрона на карті.
     * @return створений нейрон.
     */
    public Neuron create(NeuronType type, double x, double y) {
        return model.createNeuron(type, x, y);
    }

    /**
     * Видаляє нейрон і пов'язані з ним дані.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return видалений нейрон або {@code null}, якщо ідентифікатор некоректний чи нейрона немає.
     */
    public Neuron remove(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return null;
        }
        return model.removeNeuron(neuronId);
    }

    /**
     * Перемикає тип наявного нейрона між збуджувальним і гальмівним.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо нейрон знайдено й тип змінено; інакше {@code false}.
     */
    public boolean toggleType(String neuronId) {
        Neuron neuron = find(neuronId);
        if (neuron == null) {
            return false;
        }
        neuron.setType(
                neuron.type() == NeuronType.EXCITATORY
                        ? NeuronType.INHIBITORY
                        : NeuronType.EXCITATORY
        );
        return true;
    }

    /**
     * Розвертає напрямок вхідного й вихідного портів нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо подання знайдено й напрямок змінено; інакше {@code false}.
     */
    public boolean toggleDirection(String neuronId) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) {
            return false;
        }
        presentation.toggleDirection();
        return true;
    }

    /**
     * Установлює бажаний напрямок вхідного й вихідного портів нейрона.
     * Повторний виклик із тим самим значенням не змінює стан.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param directionReversed {@code true}, якщо напрямок портів потрібно розвернути.
     * @return {@code true}, якщо подання знайдено й значення застосовано; інакше {@code false}.
     */
    public boolean setDirectionReversed(String neuronId, boolean directionReversed) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) {
            return false;
        }
        presentation.setDirectionReversed(directionReversed);
        return true;
    }

    /**
     * Атомарно замінює обидва налаштування сигналу нейрона переданим value object.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param parameters нові параметри сили сигналу та порога активації.
     * @return {@code true}, якщо нейрон знайдено й параметри застосовано; інакше {@code false}.
     * @throws NullPointerException якщо параметри не задано.
     */
    public boolean updateSettings(String neuronId, NeuronParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        Neuron neuron = find(neuronId);
        if (neuron == null) {
            return false;
        }
        neuron.setParameters(parameters);
        return true;
    }

    /**
     * Оновлює силу сигналу та поріг активації нейрона.
     * Сумісний контракт делегує перевірку та збереження спільному типу параметрів.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold мінімальна сума вхідних сигналів для активації нейрона.
     * @return {@code true}, якщо нейрон знайдено й параметри оновлено; інакше {@code false}.
     * @throws IllegalArgumentException якщо будь-яке з переданих значень не є додатним.
     */
    public boolean updateSettings(String neuronId, int signalStrength, int activationThreshold) {
        Neuron neuron = find(neuronId);
        if (neuron == null) {
            return false;
        }
        neuron.setParameters(new NeuronParameters(signalStrength, activationThreshold));
        return true;
    }

    /**
     * Переміщує подання нейрона на заданий вектор зміщення.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param dx горизонтальна складова зміщення; додатне значення переміщує нейрон праворуч.
     * @param dy вертикальна складова зміщення; додатне значення переміщує нейрон униз.
     * @return {@code true}, якщо подання знайдено й переміщено; інакше {@code false}.
     */
    public boolean move(String neuronId, double dx, double dy) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) {
            return false;
        }
        presentation.moveBy(dx, dy);
        return true;
    }

    /**
     * Установлює кут обертання подання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param degrees кут обертання в градусах.
     * @return {@code true}, якщо подання знайдено й кут установлено; інакше {@code false}.
     */
    public boolean setRotation(String neuronId, double degrees) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) {
            return false;
        }
        presentation.setRotationDegrees(degrees);
        return true;
    }
}
