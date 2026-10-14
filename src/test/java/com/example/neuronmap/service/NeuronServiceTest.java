package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє створення, зміну типу, налаштувань і напрямку нейрона, а також його видалення через NeuronService.
 */
class NeuronServiceTest {

    /**
     * Перевіряє проходження створення, перемикання типу, зміни налаштувань і видалення через сервіс нейронів.
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

    /**
     * Перевіряє, що можна встановити обидва стани напрямку й що відсутній нейрон не змінюється.
     */
    @Test
    void setsDirectionToRequestedValueAndReportsMissingNeuron() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 100, 200);

        assertTrue(service.setDirectionReversed(neuron.id(), true));
        assertTrue(service.presentation(neuron.id()).directionReversed());

        assertTrue(service.setDirectionReversed(neuron.id(), false));
        assertFalse(service.presentation(neuron.id()).directionReversed());

        assertTrue(service.setDirectionReversed(neuron.id(), false));
        assertFalse(service.presentation(neuron.id()).directionReversed());

        assertFalse(service.setDirectionReversed("missing-neuron", true));
        assertFalse(service.setDirectionReversed(null, true));
    }
}
