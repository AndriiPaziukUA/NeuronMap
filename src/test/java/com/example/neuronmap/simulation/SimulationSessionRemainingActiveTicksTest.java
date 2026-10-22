package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що результати симуляції містять актуальні залишки активних тактів нейронів. */
final class SimulationSessionRemainingActiveTicksTest {

    /** Перевіряє зменшення залишку після кожного випущеного сигналу та його обнулення після останнього такту. */
    @Test
    void reportsRemainingTicksAfterEachEmission() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        source.setActiveTicks(3);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep first = session.nextStep();
        assertEquals(2, first.remainingActiveTicks().get(source.id()));

        SimulationStep second = session.nextStep();
        assertEquals(1, second.remainingActiveTicks().get(source.id()));

        SimulationStep third = session.nextStep();
        assertEquals(0, third.remainingActiveTicks().getOrDefault(source.id(), 0));
        assertFalse(third.remainingActiveTicks().containsKey(source.id()));
    }

    /**
     * Перевіряє, що такт повідомляє про досягнення порога ще під час відображення сигналу, який прямує до нейрона.
     */
    @Test
    void exposesPendingThresholdActivationBeforeItIsProcessed() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        Neuron sink = model.createNeuron(NeuronType.EXCITATORY, 200.0, 0.0);
        source.setSignalStrength(3);
        target.setActivationThreshold(3);
        target.setActiveTicks(4);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(target.id(), sink.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep step = session.nextStep();

        assertEquals(3, step.nextInputSums().get(target.id()));
        assertTrue(step.nextActivatedNeuronIds().contains(target.id()));
        assertFalse(step.activatedNeuronIds().contains(target.id()));
        assertFalse(step.remainingActiveTicks().containsKey(target.id()));
    }

    /** Перевіряє, що повторна активація перезапускає лічильник і відразу показує новий залишок. */
    @Test
    void retriggeredNeuronReportsResetCounter() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        Neuron retrigger = model.createNeuron(NeuronType.EXCITATORY, 0.0, 100.0);
        source.setActiveTicks(4);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(retrigger.id(), source.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        assertEquals(3, session.nextStep().remainingActiveTicks().get(source.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        assertEquals(2, session.nextStep().remainingActiveTicks().get(source.id()));
        SimulationStep retriggered = session.nextStep();

        assertTrue(retriggered.activatedNeuronIds().contains(source.id()));
        assertEquals(3, retriggered.remainingActiveTicks().get(source.id()));
    }

    /** Перевіряє, що результат такту зберігає незалежну незмінну копію лічильників. */
    @Test
    void remainingTickSnapshotCannotBeChangedThroughSourceMapOrAccessor() {
        Map<String, Integer> remainingTicks = new LinkedHashMap<>();
        remainingTicks.put("neuron-a", 2);
        SimulationStep step = new SimulationStep(
                BigInteger.ZERO,
                Map.of(),
                Set.of(),
                Set.of(),
                Map.of(),
                remainingTicks
        );

        remainingTicks.put("neuron-a", 1);
        assertEquals(2, step.remainingActiveTicks().get("neuron-a"));
        assertThrows(UnsupportedOperationException.class,
                () -> step.remainingActiveTicks().put("neuron-b", 3));
    }
}
