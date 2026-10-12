package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.NeuronService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Перевіряє склад і структуру демонстраційної карти для нового проєкту.
 */
final class DemoMapFactoryTest {

    /**
     * Перевіряє, що фабрика створює три нейрони в заданих позиціях і два напрямлені зв’язки.
     */
    @Test
    void createsExpectedDemoMap() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neuronService = new NeuronService(model);
        ConnectionService connectionService = new ConnectionService(model);

        DemoMapFactory.populate(neuronService, connectionService);

        assertEquals(3, model.neurons().size());
        assertEquals(2, model.connections().size());

        var neurons = List.copyOf(model.neurons());
        assertEquals(NeuronType.EXCITATORY, neurons.get(0).type());
        assertEquals(NeuronType.INHIBITORY, neurons.get(1).type());
        assertEquals(NeuronType.EXCITATORY, neurons.get(2).type());

        assertEquals(250.0, model.presentation(neurons.get(0).id()).x(), 0.001);
        assertEquals(220.0, model.presentation(neurons.get(0).id()).y(), 0.001);
        assertEquals(520.0, model.presentation(neurons.get(1).id()).x(), 0.001);
        assertEquals(340.0, model.presentation(neurons.get(1).id()).y(), 0.001);
        assertEquals(790.0, model.presentation(neurons.get(2).id()).x(), 0.001);
        assertEquals(210.0, model.presentation(neurons.get(2).id()).y(), 0.001);

        assertNotNull(model.connections().stream()
                .filter(connection -> connection.sourceId().equals(neurons.get(0).id())
                        && connection.targetId().equals(neurons.get(1).id()))
                .findFirst()
                .orElse(null));
        assertNotNull(model.connections().stream()
                .filter(connection -> connection.sourceId().equals(neurons.get(1).id())
                        && connection.targetId().equals(neurons.get(2).id()))
                .findFirst()
                .orElse(null));
    }
}
