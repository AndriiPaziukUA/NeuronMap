package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє передавання параметрів нейрона через один типізований контракт служби.
 */
class NeuronServiceParametersTest {

    /**
     * Перевіряє, що служба замінює налаштування нейрона цілісним значенням параметрів.
     */
    @Test
    void appliesParametersAsOneValue() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 0.0, 0.0);
        NeuronParameters parameters = new NeuronParameters(8, 5);

        assertTrue(service.updateSettings(neuron.id(), parameters));
        assertEquals(parameters, neuron.parameters());
    }

    /**
     * Перевіряє, що оновлення відсутнього нейрона повертає false без створення побічних ефектів.
     */
    @Test
    void missingNeuronIsNotUpdated() {
        NeuronService service = new NeuronService(new NeuronMapModel());

        assertFalse(service.updateSettings("missing", new NeuronParameters(3, 2)));
    }
}
