package com.example.neuronmap.simulation;

import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Перевіряє застосування runtime-активацій симуляції окремо від компонента відображення. */
final class SimulationRuntimeStateTest {

    @Test
    void applyReplacesPreviousActivationsWithCurrentInputSums() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron second = model.createNeuron(NeuronType.INHIBITORY, 100.0, 0.0);
        first.setActivation(7);
        second.setActivation(9);
        SimulationRuntimeState runtimeState = new SimulationRuntimeState(model);

        runtimeState.apply(new SimulationStep(
                BigInteger.valueOf(3),
                Map.of(first.id(), 2, "missing-neuron", 6),
                Set.of(first.id()),
                Map.of()
        ));

        assertEquals(2, first.activation());
        assertEquals(0, second.activation());
    }

    /** Перевіряє, що очищення runtime-стану скидає активації всіх нейронів до нуля. */
    @Test
    void clearResetsAllRuntimeActivations() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron first = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron second = model.createNeuron(NeuronType.INHIBITORY, 100.0, 0.0);
        first.setActivation(3);
        second.setActivation(-2);

        new SimulationRuntimeState(model).clear();

        assertEquals(0, first.activation());
        assertEquals(0, second.activation());
    }

    /** Перевіряє, що підготовка даних для відображення не залежить від служби зміни доменної моделі нейрона. */
    @Test
    void presenterDoesNotDependOnDomainMutationService() {
        boolean usesNeuronService = java.util.Arrays.stream(
                SimulationStepPresenter.class.getDeclaredFields()
        ).anyMatch(field -> field.getType().equals(NeuronService.class));

        assertFalse(usesNeuronService);
    }
}
