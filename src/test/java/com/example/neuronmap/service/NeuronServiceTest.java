package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє контракти пошуку, видалення та зміни стану нейронів через NeuronService. */
class NeuronServiceTest {

    /** Перевіряє створення, зміну типу, налаштувань і видалення через службу нейронів. */
    @Test
    void createToggleSettingsAndRemoveAreDelegatedThroughService() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 100, 200);

        assertEquals(NeuronType.EXCITATORY, neuron.type());
        assertEquals(neuron, service.find(neuron.id()).orElseThrow());
        assertTrue(service.toggleType(neuron.id()));
        assertEquals(NeuronType.INHIBITORY, neuron.type());
        assertTrue(service.updateSettings(neuron.id(), 7, 5));
        assertEquals(7, neuron.signalStrength());
        assertEquals(5, neuron.activationThreshold());
        assertTrue(service.presentation(neuron.id()).isPresent());
        assertEquals(neuron, service.remove(neuron.id()).orElseThrow());
        assertTrue(service.find(neuron.id()).isEmpty());
    }

    /** Перевіряє, що пошук повертає порожній Optional для некоректних або відсутніх ідентифікаторів. */
    @Test
    void lookupsRepresentMissingNeuronsWithEmptyOptionals() {
        NeuronService service = new NeuronService(new NeuronMapModel());

        assertTrue(service.find(null).isEmpty());
        assertTrue(service.find(" ").isEmpty());
        assertTrue(service.find("missing").isEmpty());
        assertTrue(service.presentation(null).isEmpty());
        assertTrue(service.presentation("missing").isEmpty());
        assertTrue(service.remove(null).isEmpty());
        assertTrue(service.remove("missing").isEmpty());
    }

    /** Перевіряє застосування заданого напрямку та безпечну поведінку для відсутнього нейрона. */
    @Test
    void setsDirectionToRequestedValueAndReportsMissingNeuron() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService service = new NeuronService(model);
        Neuron neuron = service.create(NeuronType.EXCITATORY, 100, 200);

        assertTrue(service.setDirectionReversed(neuron.id(), true));
        assertTrue(service.presentation(neuron.id()).orElseThrow().directionReversed());
        assertTrue(service.setDirectionReversed(neuron.id(), false));
        assertFalse(service.presentation(neuron.id()).orElseThrow().directionReversed());
        assertTrue(service.setDirectionReversed(neuron.id(), false));
        assertFalse(service.presentation(neuron.id()).orElseThrow().directionReversed());
        assertFalse(service.setDirectionReversed("missing-neuron", true));
        assertFalse(service.setDirectionReversed(null, true));
    }
}
