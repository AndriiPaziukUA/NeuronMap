package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.history.FieldStateSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє продовження вже запущеного сеансу після відновлення нейрона через знімок історії. */
class SimulationSessionModelRestorationTest {

    /** Перевіряє, що відновлений нейрон приймає очікуваний сигнал і передає його далі без перезапуску сеансу. */
    @Test
    void restoredNeuronContinuesPendingSignalInExistingSession() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron restored = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        Neuron downstream = model.createNeuron(NeuronType.EXCITATORY, 200.0, 0.0);
        assertTrue(model.createConnection(source.id(), restored.id()));
        assertTrue(model.createConnection(restored.id(), downstream.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        assertNotNull(session.nextStep());
        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);

        assertNotNull(model.removeNeuron(restored.id()));
        snapshot.restoreInto(model);

        SimulationStep resumedStep = session.nextStep();
        assertNotNull(resumedStep);
        assertTrue(resumedStep.activatedNeuronIds().contains(restored.id()));
        assertTrue(resumedStep.nextInputSums().containsKey(downstream.id()));
    }
}
