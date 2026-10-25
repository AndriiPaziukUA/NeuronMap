package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє затримку першого вихідного сигналу та її взаємодію з тривалістю активності нейрона. */
final class SimulationSessionDelayTest {

    /** Перевіряє, що затримка в один такт зберігає початкову поведінку передавання сигналу. */
    @Test
    void oneTickDelayEmitsDuringActivationTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));

        SimulationSession session = SimulationSession.manual(model, source.id());
        SimulationStep activation = session.nextStep();
        assertNotNull(activation, "Очікується результат першого такту симуляції");

        assertEquals(Set.of(source.id()), activation.emittingNeuronIds());
        assertEquals(1, activation.nextInputSums().get(target.id()));
        assertEquals(0, activation.tick().intValue());
        assertTrue(session.isFinished(), "Недостатній сигнал не повинен спричиняти ще один такт");
    }

    /** Перевіряє, що затримка у два такти пропускає перший такт активації перед передаванням сигналу. */
    @Test
    void twoTickDelaySkipsActivationTickAndEmitsOnFollowingTick() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setDelayTicks(2);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep waiting = session.nextStep();
        assertNotNull(waiting, "Очікується результат такту очікування затримки");
        assertTrue(waiting.activatedNeuronIds().contains(source.id()));
        assertFalse(waiting.emittingNeuronIds().contains(source.id()));
        assertTrue(waiting.nextInputSums().isEmpty());
        assertTrue(waiting.remainingActiveTicks().isEmpty());
        assertTrue(session.hasPendingWork());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission, "Очікується результат такту передавання сигналу");
        assertEquals(Set.of(source.id()), emission.emittingNeuronIds());
        assertEquals(1, emission.nextInputSums().get(target.id()));
        assertTrue(emission.remainingActiveTicks().isEmpty(),
                "Однотиковий період активності вже витрачено під час першого випуску сигналу");
        assertTrue(session.isFinished(),
                "Після затримки та випуску недостатнього сигналу доставка не створює додаткового такту");

        assertNull(session.nextStep());
    }

    /** Перевіряє, що тривалість активності відраховується лише після завершення затримки та першого випуску. */
    @Test
    void activeTickCounterStartsOnlyWhenFirstSignalIsActuallyEmitted() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        source.setDelayTicks(3);
        source.setActiveTicks(2);
        target.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep firstWaitingTick = session.nextStep();
        assertNotNull(firstWaitingTick, "Очікується результат першого такту затримки");
        assertTrue(firstWaitingTick.remainingActiveTicks().isEmpty());
        assertFalse(firstWaitingTick.emittingNeuronIds().contains(source.id()));
        SimulationStep secondWaitingTick = session.nextStep();
        assertNotNull(secondWaitingTick, "Очікується результат другого такту затримки");
        assertTrue(secondWaitingTick.remainingActiveTicks().isEmpty());
        assertFalse(secondWaitingTick.emittingNeuronIds().contains(source.id()));

        SimulationStep firstEmission = session.nextStep();
        assertNotNull(firstEmission, "Очікується результат першого такту випуску сигналу");
        assertEquals(Set.of(source.id()), firstEmission.emittingNeuronIds());
        assertEquals(1, firstEmission.remainingActiveTicks().get(source.id()));
        assertEquals(1, firstEmission.nextInputSums().get(target.id()));

        SimulationStep secondEmission = session.nextStep();
        assertNotNull(secondEmission, "Очікується результат другого такту випуску сигналу");
        assertEquals(Set.of(source.id()), secondEmission.emittingNeuronIds());
        assertTrue(secondEmission.remainingActiveTicks().isEmpty());
        assertEquals(1, secondEmission.nextInputSums().get(target.id()));
        assertTrue(session.isFinished());

        assertNull(session.nextStep(), "Недостатній сигнал не повинен створювати порожній такт доставки");
    }

    /** Перевіряє, що повторна активація перезапускає затримку, не залишаючи старий активний лічильник. */
    @Test
    void reactivationRestartsDelayBeforeResumingEmission() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron retrigger = model.createNeuron(NeuronType.EXCITATORY, 0, 100);
        source.setDelayTicks(2);
        source.setActiveTicks(2);
        target.setActivationThreshold(100);
        retrigger.setActivationThreshold(100);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(retrigger.id(), source.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep initialDelayTick = session.nextStep();
        assertNotNull(initialDelayTick, "Очікується результат початкового такту затримки");
        assertFalse(initialDelayTick.emittingNeuronIds().contains(source.id()));
        assertTrue(session.queueManualStart(retrigger.id()));
        SimulationStep retriggerTick = session.nextStep();
        assertNotNull(retriggerTick, "Очікується результат такту повторного запуску");
        assertTrue(retriggerTick.activatedNeuronIds().contains(retrigger.id()));
        assertTrue(retriggerTick.emittingNeuronIds().contains(source.id()),
                "Первісна затримка джерела має завершитися незалежно від нового вхідного сигналу");

        SimulationStep sourceRetriggered = session.nextStep();
        assertNotNull(sourceRetriggered, "Очікується результат такту повторної активації джерела");
        assertTrue(sourceRetriggered.activatedNeuronIds().contains(source.id()));
        assertFalse(sourceRetriggered.emittingNeuronIds().contains(source.id()));
        assertTrue(sourceRetriggered.remainingActiveTicks().isEmpty());

        SimulationStep emission = session.nextStep();
        assertNotNull(emission, "Очікується результат такту після повторної затримки");
        assertTrue(emission.emittingNeuronIds().contains(source.id()));
        assertEquals(1, emission.nextInputSums().get(target.id()));
    }
}
