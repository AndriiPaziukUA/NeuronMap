package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Надає операції над нейронами: створення, пошук, видалення, переміщення, обертання,
 * зміну типу, напрямку, назви та параметрів сигналу. Під час створення структури додає її початкового учасника.
 */
public final class NeuronService {

    private final NeuronMapModel model;
    /** Єдина служба, через яку ця служба змінює членство нейронів у структурах. */
    private final StructureService structureService;

    /**
     * Створює службу операцій над нейронами для заданої моделі карти.
     *
     * @param model модель карти нейронів, над якою виконуватимуться операції.
     * @throws NullPointerException якщо модель не задано.
     */
    public NeuronService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
        this.structureService = new StructureService(this.model);
    }

    /** Повертає модель карти, якою керує ця служба. @return модель карти нейронів. */
    public NeuronMapModel model() { return model; }

    /** Повертає поточні нейрони моделі. @return колекція нейронів моделі без створення окремої копії. */
    public Collection<Neuron> neurons() { return model.neurons(); }

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
     * Створює нейрон указаного типу в заданих координатах із параметрами за замовчуванням.
     * Структура одразу отримує одного збуджувального учасника; наступні учасники додаються окремо без ліміту.
     *
     * @param type тип нейрона.
     * @param x початкова горизонтальна координата нейрона на карті.
     * @param y початкова вертикальна координата нейрона на карті.
     * @return створений нейрон з одним активним тактом і затримкою в один такт.
     * @throws NullPointerException якщо тип нейрона не задано.
     * @throws IllegalStateException якщо не вдалося зареєструвати початкового учасника нової структури.
     */
    public Neuron create(NeuronType type, double x, double y) {
        Objects.requireNonNull(type, "type");
        Neuron created = model.createNeuron(type, x, y);
        if (type != NeuronType.STRUCTURE) {
            return created;
        }

        Neuron initialMember = null;
        try {
            // Внутрішній нейрон не має власної позиції на дошці; його координати задає шухлядка.
            initialMember = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
            if (!structureService.addMember(created.id(), initialMember.id())) {
                throw new IllegalStateException("Could not register the initial neuron in the new structure");
            }
            return created;
        } catch (RuntimeException failure) {
            if (initialMember != null) {
                model.removeNeuron(initialMember.id());
            }
            model.removeNeuron(created.id());
            throw failure;
        }
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
        Neuron removed = model.neuron(neuronId);
        if (removed == null) {
            return Optional.empty();
        }
        removeStructureMembersRecursively(neuronId, new LinkedHashSet<>());
        return Optional.ofNullable(model.removeNeuron(neuronId));
    }

    /**
     * Видаляє всіх вкладених учасників перед видаленням структури-власника.
     *
     * @param structureId ідентифікатор структури або нейрона, який може містити учасників.
     * @param visited ідентифікатори, уже оброблені під час рекурсивного обходу.
     */
    private void removeStructureMembersRecursively(String structureId, Set<String> visited) {
        if (!visited.add(structureId)) {
            return;
        }
        structureService.find(structureId).ifPresent(structure -> {
            for (String memberId : java.util.List.copyOf(structure.memberIds())) {
                removeStructureMembersRecursively(memberId, visited);
                model.removeNeuron(memberId);
            }
        });
    }

    /**
     * Перемикає тип нейрона або типи звичайних нейронів усередині структури.
     * Контейнери структур зберігають свій структурний тип; для вкладених структур змінюються їхні учасники.
     *
     * @param neuronId ідентифікатор нейрона або структури.
     * @return {@code true}, якщо знайдений нейрон або хоча б один учасник отримав новий тип.
     */
    public boolean toggleType(String neuronId) {
        Optional<Neuron> neuron = find(neuronId);
        if (neuron.isEmpty()) {
            return false;
        }
        if (structureService.find(neuronId).isPresent()) {
            return toggleStructureMemberTypesRecursively(neuronId, new LinkedHashSet<>());
        }
        return toggleNeuronType(neuron.orElseThrow());
    }

    /**
     * Перемикає типи звичайних нейронів у дереві структури, не змінюючи типи самих контейнерів.
     * Набір відвіданих ідентифікаторів не допускає повторної обробки за наявності циклічних посилань.
     *
     * @param structureId ідентифікатор структури, учасників якої потрібно обробити.
     * @param visited ідентифікатори контейнерів, уже оброблених під час обходу.
     * @return {@code true}, якщо тип принаймні одного звичайного нейрона змінено.
     */
    private boolean toggleStructureMemberTypesRecursively(String structureId, Set<String> visited) {
        if (!visited.add(structureId)) {
            return false;
        }
        Optional<NeuronStructure> structure = structureService.find(structureId);
        if (structure.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (String memberId : structure.orElseThrow().memberIds()) {
            if (structureService.find(memberId).isPresent()) {
                changed |= toggleStructureMemberTypesRecursively(memberId, visited);
            } else {
                changed |= find(memberId).map(this::toggleNeuronType).orElse(false);
            }
        }
        return changed;
    }

    /**
     * Перемикає нейрон між активуючою та гальмівною категоріями.
     *
     * @param neuron нейрон, тип якого потрібно змінити.
     * @return {@code true}, якщо тип змінено.
     */
    private boolean toggleNeuronType(Neuron neuron) {
        neuron.setType(neuron.type().isActivating() ? NeuronType.INHIBITORY : NeuronType.EXCITATORY);
        return true;
    }

    /**
     * Розвертає напрямок нейрона, а для структури — також напрямки всіх нейронів у вкладеному дереві.
     *
     * @param neuronId ідентифікатор нейрона або структури.
     * @return {@code true}, якщо подання знайдено й напрямок змінено хоча б для цільового нейрона.
     */
    public boolean toggleDirection(String neuronId) {
        if (presentation(neuronId).isEmpty()) {
            return false;
        }
        if (structureService.find(neuronId).isPresent()) {
            return toggleStructureDirectionsRecursively(neuronId, new LinkedHashSet<>());
        }
        return toggleNeuronDirection(neuronId);
    }

    /**
     * Розвертає напрямок контейнера й усіх його учасників, включно з вкладеними структурами.
     *
     * @param structureId ідентифікатор структури, напрямки якої потрібно розвернути.
     * @param visited ідентифікатори контейнерів, уже оброблених під час обходу.
     * @return {@code true}, якщо напрямок контейнера змінено.
     */
    private boolean toggleStructureDirectionsRecursively(String structureId, Set<String> visited) {
        if (!visited.add(structureId)) {
            return false;
        }
        Optional<NeuronStructure> structure = structureService.find(structureId);
        if (structure.isEmpty()) {
            return false;
        }

        boolean changed = toggleNeuronDirection(structureId);
        for (String memberId : structure.orElseThrow().memberIds()) {
            if (structureService.find(memberId).isPresent()) {
                changed |= toggleStructureDirectionsRecursively(memberId, visited);
            } else {
                changed |= toggleNeuronDirection(memberId);
            }
        }
        return changed;
    }

    /**
     * Перемикає напрямок портів одного нейрона без рекурсивної обробки його можливих учасників.
     *
     * @param neuronId ідентифікатор нейрона.
     * @return {@code true}, якщо подання знайдено й напрямок змінено; інакше {@code false}.
     */
    private boolean toggleNeuronDirection(String neuronId) {
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
     * Атомарно замінює всі параметри нейрона переданим незмінним об'єктом, включно із затримкою передачі.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param parameters сила сигналу, поріг активації, кількість активних тактів і затримка.
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
     * Оновлює параметри нейрона й назву як одну операцію служби.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param parameters усі числові параметри сигналу, включно із затримкою.
     * @param name нова назва або {@code null}, щоб очистити її.
     * @return {@code true}, якщо нейрон знайдено й обидва значення застосовано; інакше {@code false}.
     * @throws NullPointerException якщо параметри сигналу не задано.
     * @throws IllegalArgumentException якщо назва перевищує допустиму довжину.
     */
    public boolean updateSettings(String neuronId, NeuronParameters parameters, String name) {
        Objects.requireNonNull(parameters, "parameters");
        return find(neuronId).map(neuron -> {
            neuron.setName(name);
            neuron.setParameters(parameters);
            return true;
        }).orElse(false);
    }

    /**
     * Оновлює силу сигналу та поріг активації, зберігаючи інші параметри, зокрема затримку й активні такти.
     * Цей метод залишає чинний двопараметровий контракт для наявних викликів.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param signalStrength сила сигналу, який генерує нейрон.
     * @param activationThreshold мінімальна сума вхідних сигналів для активації нейрона.
     * @return {@code true}, якщо нейрон знайдено й параметри оновлено; інакше {@code false}.
     * @throws IllegalArgumentException якщо будь-яке з переданих значень не є додатним.
     */
    public boolean updateSettings(String neuronId, int signalStrength, int activationThreshold) {
        NeuronParameters parameters = new NeuronParameters(signalStrength, activationThreshold);
        return find(neuronId).map(neuron -> {
            neuron.setSignalStrength(parameters.signalStrength());
            neuron.setActivationThreshold(parameters.activationThreshold());
            return true;
        }).orElse(false);
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
