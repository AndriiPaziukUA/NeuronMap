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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє залишки активних тактів, прогнозування активацій і захист від дублювання імпульсів. */
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

        assertNotNull(first);
        assertEquals(2, first.remainingActiveTicks().get(source.id()));

        SimulationStep second = session.nextStep();

        assertNotNull(second);
        assertEquals(1, second.remainingActiveTicks().get(source.id()));

        SimulationStep third = session.nextStep();

        assertNotNull(third);
        assertEquals(0, third.remainingActiveTicks().getOrDefault(source.id(), 0));
        assertFalse(third.remainingActiveTicks().containsKey(source.id()));
    }

    /** Перевіряє, що такт повідомляє про досягнення порога ще під час відображення сигналу, який прямує до нейрона. */
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

        assertNotNull(step);

        assertEquals(3, step.nextInputSums().get(target.id()));
        assertTrue(step.nextActivatedNeuronIds().contains(target.id()));
        assertFalse(step.activatedNeuronIds().contains(target.id()));
        assertFalse(step.remainingActiveTicks().containsKey(target.id()));
    }

    /** Перевіряє, що повторна активація не додає другий сигнал, доки сигнал нейрона перебуває в дорозі. */
    @Test
    void immediateActivationIsIgnoredWhileNeuronIsAlreadyEmittingThisTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        source.setActiveTicks(4);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep first = session.nextStep();

        assertNotNull(first);
        assertEquals(1, first.nextInputSums().get(target.id()));
        assertEquals(3, first.remainingActiveTicks().get(source.id()));
        BigInteger nextTick = session.tick();

        assertNull(session.activateManualStartNow(source.id()));
        assertEquals(nextTick, session.tick());

        SimulationStep next = session.nextStep();

        assertNotNull(next);
        assertEquals(1, next.inputSums().get(target.id()));
        assertEquals(1, next.nextInputSums().get(target.id()));
        assertEquals(2, next.remainingActiveTicks().get(source.id()));
    }

    /** Перевіряє, що повторна негайна активація не спричиняє зайвого такту для сигналу нижче порога. */
    @Test
    void repeatedImmediateActivationDoesNotAccumulateDuplicateSignals() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());
        SimulationStep first = session.nextStep();
        assertNotNull(first);
        assertEquals(1, first.nextInputSums().get(target.id()));

        assertNull(session.activateManualStartNow(source.id()));
        assertNull(session.nextStep());
        assertTrue(session.isFinished());
        assertFalse(session.hasPendingWork());
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

        SimulationStep initial = session.nextStep();
        assertNotNull(initial);
        assertEquals(3, initial.remainingActiveTicks().get(source.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        SimulationStep beforeRetrigger = session.nextStep();
        assertNotNull(beforeRetrigger);
        assertEquals(2, beforeRetrigger.remainingActiveTicks().get(source.id()));
        SimulationStep retriggered = session.nextStep();
        assertNotNull(retriggered);

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

    /** Перевіряє негайний випуск сигналу, об'єднання черги сигналів і відсутність зсуву глобального такту. */
    @Test
    void manualActivationImmediatelyMergesSignalsWithoutAdvancingTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron firstSource = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100.0, 0.0);
        Neuron manualSource = model.createNeuron(NeuronType.EXCITATORY, 0.0, 100.0);
        target.setActivationThreshold(2);
        target.setActiveTicks(3);
        manualSource.setActiveTicks(4);
        assertTrue(model.createConnection(firstSource.id(), target.id()));
        assertTrue(model.createConnection(manualSource.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, firstSource.id());

        SimulationStep first = session.nextStep();

        assertNotNull(first);
        BigInteger nextTick = session.tick();
        SimulationStep immediate = session.activateManualStartNow(manualSource.id());

        assertNotNull(immediate);
        assertEquals(first.tick(), immediate.tick());
        assertEquals(nextTick, session.tick());
        assertEquals(2, immediate.nextInputSums().get(target.id()));
        assertTrue(immediate.nextActivatedNeuronIds().contains(target.id()));
        assertTrue(immediate.activatedNeuronIds().contains(manualSource.id()));
        assertEquals(Set.of(firstSource.id(), manualSource.id()), immediate.emittingNeuronIds());
        assertEquals(3, immediate.remainingActiveTicks().get(manualSource.id()));

        SimulationStep delivered = session.nextStep();

        assertNotNull(delivered);
        assertEquals(nextTick, delivered.tick());
        assertEquals(2, delivered.inputSums().get(target.id()));
        assertTrue(delivered.activatedNeuronIds().contains(target.id()));
    }

    /** Перевіряє, що некоректний ідентифікатор не змінює чергу й стан сеансу симуляції. */
    @Test
    void immediateActivationRejectsUnknownNeuronWithoutChangingTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        SimulationSession session = SimulationSession.manual(model, source.id());
        session.nextStep();
        BigInteger currentTick = session.tick();

        assertNull(session.activateManualStartNow("missing-neuron"));
        assertEquals(currentTick, session.tick());
        assertFalse(session.hasPendingWork());
    }
}
