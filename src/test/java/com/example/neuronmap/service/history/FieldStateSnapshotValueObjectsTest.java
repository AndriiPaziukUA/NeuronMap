package com.example.neuronmap.service.history;

import com.example.neuronmap.model.MapPosition;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Перевіряє, що знімок історії використовує value object-и для параметрів нейрона та позиції його подання.
 */
class FieldStateSnapshotValueObjectsTest {

    /**
     * Перевіряє, що знімок фіксує узгоджені об'єкти параметрів і позиції та відновлює їхні значення.
     */
    @Test
    void capturesAndRestoresValueObjects() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(NeuronType.INHIBITORY, 32.0, 48.0, 15.0);
        neuron.setParameters(new NeuronParameters(6, 4));

        FieldStateSnapshot snapshot = FieldStateSnapshot.capture(model);
        assertEquals(new NeuronParameters(6, 4), snapshot.neurons().get(0).parameters());

        neuron.setParameters(new NeuronParameters(2, 1));
        model.presentation(neuron.id()).setPosition(new MapPosition(100.0, 200.0));
        snapshot.restoreInto(model);

        assertEquals(new NeuronParameters(6, 4), model.neuron(neuron.id()).parameters());
        assertEquals(new MapPosition(32.0, 48.0), model.presentation(neuron.id()).position());
    }
}
