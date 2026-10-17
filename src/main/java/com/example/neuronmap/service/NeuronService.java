package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

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
     * @return знайдений нейрон або порожній результат, якщо ідентифікатор некоректний чи нейрона немає.
     */
    public Optional<Neuron> find(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.neuron(neuronId));
    }

    /**
     * Повертає візуальне подання нейрона за його ідентифікатором.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return подання нейрона або порожній результат, якщо ідентифікатор некоректний чи нейрон відсутній.
     */
    public Optional<NeuronPresentation> presentation(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.presentation(neuronId));
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
     * @return видалений нейрон або порожній результат, якщо ідентифікатор некоректний чи нейрона немає.
     */
    public Optional<Neuron> remove(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.removeNeuron(neuronId));
    }

    /**
     * Перемикає тип наявного нейрона між збуджувальним і гальмівним.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо нейрон знайдено й тип змінено; інакше {@code false}.
     */
    public boolean toggleType(String neuronId) {
        return find(neuronId).map(neuron -> {
            neuron.setType(
                    neuron.type() == NeuronType.EXCITATORY
                            ? NeuronType.INHIBITORY
                            : NeuronType.EXCITATORY
            );
            return true;
        }).orElse(false);
    }

    /**
     * Розвертає напрямок вхідного й вихідного портів нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо подання знайдено й напрямок змінено; інакше {@code false}.
     */
    public boolean toggleDirection(String neuronId) {
        return presentation(neuronId).map(presentation -> {
            presentation.toggleDirection();
            return true;
        }).orElse(false);
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
        return presentation(neuronId).map(presentation -> {
            presentation.setDirectionReversed(directionReversed);
            return true;
        }).orElse(false);
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
        return find(neuronId).map(neuron -> {
            neuron.setParameters(parameters);
            return true;
        }).orElse(false);
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
        NeuronParameters parameters = new NeuronParameters(signalStrength, activationThreshold);
        return updateSettings(neuronId, parameters);
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
        return presentation(neuronId).map(presentation -> {
            presentation.moveBy(dx, dy);
            return true;
        }).orElse(false);
    }

    /**
     * Установлює кут обертання подання нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param degrees кут обертання в градусах.
     * @return {@code true}, якщо подання знайдено й кут установлено; інакше {@code false}.
     */
    public boolean setRotation(String neuronId, double degrees) {
        return presentation(neuronId).map(presentation -> {
            presentation.setRotationDegrees(degrees);
            return true;
        }).orElse(false);
    }
}
