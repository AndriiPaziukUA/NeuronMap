package com.example.neuronmap.service;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє операції зі зв’язками та контракт Optional для пошуку зв’язку. */
class ConnectionServiceTest {

    /** Перевіряє створення, пошук, наявність і видалення зв’язку через службу. */
    @Test
    void connectionOperationsStayOutsideControllers() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neurons = new NeuronService(model);
        ConnectionService connections = new ConnectionService(model);
        String first = neurons.create(NeuronType.EXCITATORY, 0, 0).id();
        String second = neurons.create(NeuronType.INHIBITORY, 100, 0).id();

        assertTrue(connections.create(first, second));
        assertFalse(connections.create(first, second));
        assertTrue(connections.hasConnections(first));
        var connection = connections.find(model.connections().iterator().next().id()).orElseThrow();
        assertEquals(first, connection.sourceId());
        assertEquals(second, connection.targetId());
        assertTrue(connections.find("missing").isEmpty());
        assertEquals(1, connections.removeBetween(first, second));
        assertFalse(connections.hasConnections(first));
    }

    /** Перевіряє, що некоректні пошукові ідентифікатори дають порожній результат, а не null. */
    @Test
    void invalidConnectionLookupUsesEmptyOptional() {
        ConnectionService connections = new ConnectionService(new NeuronMapModel());

        assertTrue(connections.find(null).isEmpty());
        assertTrue(connections.find("").isEmpty());
    }
}
