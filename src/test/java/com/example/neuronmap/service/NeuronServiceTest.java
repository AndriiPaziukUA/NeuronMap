package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє операції служби нейронів та сумісність двопараметрового оновлення налаштувань. */
class NeuronServiceTest {

    @Test
    void legacyUpdateSettingsOverloadPreservesActiveTicks() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 10.0, 20.0);
        neuron.setParameters(new NeuronParameters(2, 3, 5));

        assertTrue(service.updateSettings(neuron.id(), 7, 9));
        assertEquals(7, neuron.signalStrength());
        assertEquals(9, neuron.activationThreshold());
        assertEquals(5, neuron.parameters().activeTicks());
        assertFalse(service.updateSettings("missing-neuron", 7, 9));
    }

    @Test
    void moveReportsWhetherTheNeuronPresentationWasFound() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 10.0, 20.0);

        assertTrue(service.move(neuron.id(), 3.5, -4.0));
        assertEquals(13.5, service.presentation(neuron.id()).orElseThrow().x());
        assertEquals(16.0, service.presentation(neuron.id()).orElseThrow().y());
        assertFalse(service.move("missing-neuron", 1.0, 1.0));
    }
}
