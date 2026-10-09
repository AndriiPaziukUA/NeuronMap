package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє правила створення й видалення звʼязків.
 */
class ConnectionServiceTest {

    /**
     * Перевіряє очікувану поведінку: звʼязок.
     */
    @Test
    void connectionOperationsStayOutsideControllers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        ConnectionService connections = new ConnectionService(model);

        String first = neurons.create(
                NeuronType.EXCITATORY,
                0,
                0
        ).id();
        String second = neurons.create(
                NeuronType.INHIBITORY,
                100,
                0
        ).id();

        assertTrue(connections.create(first, second));
        assertFalse(connections.create(first, second));
        assertTrue(connections.hasConnections(first));
        assertEquals(1, connections.removeBetween(first, second));
        assertFalse(connections.hasConnections(first));
    }
}
