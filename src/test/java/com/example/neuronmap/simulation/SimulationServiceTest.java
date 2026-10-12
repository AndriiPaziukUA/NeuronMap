package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє створення сеансів симуляції через SimulationService.
 */
final class SimulationServiceTest {

    /**
     * Перевіряє, що служба створює сеанс із ручним запуском наявного нейрона.
     */
    @Test
    void startsManualSessionForExistingNeuron() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 100.0, 120.0);

        SimulationSession session = new SimulationService().start(model, source.id());

        assertNotNull(session);
        assertTrue(session.hasPendingWork());
        assertFalse(session.isFinished());
    }

    /**
     * Перевіряє, що служба не створює сеанс для відсутнього нейрона.
     */
    @Test
    void rejectsUnknownNeuron() {
        SimulationSession session = new SimulationService().start(
                new NeuronMapModel(),
                "missing-neuron"
        );

        assertNull(session);
    }

    /**
     * Перевіряє, що служба безпечно відхиляє відсутню модель та ідентифікатор початкового нейрона.
     */
    @Test
    void rejectsNullModelOrNeuronId() {
        SimulationService service = new SimulationService();
        NeuronMapModel model = new NeuronMapModel();

        assertNull(service.start(null, "neuron"));
        assertNull(service.start(model, null));
    }
}
