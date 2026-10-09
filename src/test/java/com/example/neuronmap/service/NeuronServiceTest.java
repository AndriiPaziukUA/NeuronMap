package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє створення, пошук, зміну параметрів і видалення нейронів.
 */
class NeuronServiceTest {

    /**
     * Перевіряє очікувану поведінку: створити перемкнути налаштування і видалити служба.
     */
    @Test
    void createToggleSettingsAndRemoveAreDelegatedThroughService() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);

        Neuron neuron = service.create(
                NeuronType.EXCITATORY,
                100,
                200
        );

        assertNotNull(neuron);
        assertEquals(NeuronType.EXCITATORY, neuron.type());
        assertEquals(neuron, service.find(neuron.id()));

        assertTrue(service.toggleType(neuron.id()));
        assertEquals(NeuronType.INHIBITORY, neuron.type());

        assertTrue(service.updateSettings(neuron.id(), 7, 5));
        assertEquals(7, neuron.signalStrength());
        assertEquals(5, neuron.activationThreshold());

        assertNotNull(service.presentation(neuron.id()));
        assertTrue(service.remove(neuron.id()) == neuron);
    }
}
