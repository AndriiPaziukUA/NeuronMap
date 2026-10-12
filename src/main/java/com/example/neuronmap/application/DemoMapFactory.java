package com.example.neuronmap.application;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.NeuronService;

import java.util.Objects;

/**
 * Створює початкову демонстраційну карту для нового проєкту.
 */
public final class DemoMapFactory {

    /**
     * Забороняє створення екземплярів фабрики демонстраційної карти.
     */
    private DemoMapFactory() {
    }

    /**
     * Додає до моделі три демонстраційні нейрони та два зв’язки між ними.
     *
     * @param neuronService служба створення нейронів.
     * @param connectionService служба створення зв’язків між нейронами.
     * @throws NullPointerException якщо одну зі служб не задано.
     * @throws IllegalArgumentException якщо служби працюють із різними моделями.
     */
    public static void populate(
            NeuronService neuronService,
            ConnectionService connectionService
    ) {
        Objects.requireNonNull(neuronService, "neuronService");
        Objects.requireNonNull(connectionService, "connectionService");

        NeuronMapModel model = neuronService.model();
        if (connectionService.model() != model) {
            throw new IllegalArgumentException("Both services must use the same model.");
        }

        Neuron first = neuronService.create(NeuronType.EXCITATORY, 250, 220);
        Neuron second = neuronService.create(NeuronType.INHIBITORY, 520, 340);
        Neuron third = neuronService.create(NeuronType.EXCITATORY, 790, 210);
        connectionService.create(first.id(), second.id());
        connectionService.create(second.id(), third.id());
    }
}
