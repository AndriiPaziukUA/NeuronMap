package com.example.neuronmap.simulation;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє відсотковий поріг, знаковий лічильник входів і регресію старого режиму симуляції. */
class SimulationSessionActivationModeTest {

    /** Перевіряє приклади округлення порога вгору на межах відсоткових діапазонів. */
    @Test
    void percentageThresholdRoundsUpAcrossThreeExcitatoryInputs() {
        assertActivation(0, 1, true);
        assertActivation(33, 1, true);
        assertActivation(34, 1, false);
        assertActivation(34, 2, true);
        assertActivation(66, 1, false);
        assertActivation(66, 2, true);
        assertActivation(67, 2, false);
        assertActivation(67, 3, true);
        assertActivation(100, 3, true);
    }

    /** Переконується, що гальмівні входи не збільшують знаменник відсоткового порога. */
    @Test
    void inhibitoryConnectionsAreExcludedFromPercentageDenominator() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron excitatory = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron inactiveExcitatory = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron inhibitory = model.createNeuron(NeuronType.INHIBITORY, 200, 0);
        Neuron target = countModeTarget(model, 50);
        assertTrue(model.createConnection(excitatory.id(), target.id()));
        assertTrue(model.createConnection(inactiveExcitatory.id(), target.id()));
        assertTrue(model.createConnection(inhibitory.id(), target.id()));

        SimulationStep step = SimulationSession.manual(model, excitatory.id()).nextStep();

        assertNotNull(step);
        assertEquals(1, step.nextInputSums().get(target.id()));
        assertTrue(step.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Перевіряє, що збуджувальний і гальмівний сигнали взаємно компенсуються в одному такті. */
    @Test
    void simultaneousExcitatoryAndInhibitorySignalsCancelEachOther() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron excitatory = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron inhibitory = model.createNeuron(NeuronType.INHIBITORY, 100, 0);
        Neuron target = countModeTarget(model, 0);
        assertTrue(model.createConnection(excitatory.id(), target.id()));
        assertTrue(model.createConnection(inhibitory.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, excitatory.id());
        assertTrue(session.queueManualStart(inhibitory.id()));

        SimulationStep step = session.nextStep();

        assertNotNull(step);
        assertEquals(0, step.nextInputSums().get(target.id()));
        assertFalse(step.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Переконується, що нейрон без входів не активується автоматично за нульового відсоткового порога. */
    @Test
    void zeroPercentDoesNotActivateTargetWithoutAnyInput() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        Neuron target = countModeTarget(model, 0);
        SimulationStep step = SimulationSession.manual(model, source.id()).nextStep();

        assertNotNull(step);
        assertFalse(step.nextInputSums().containsKey(target.id()));
        assertFalse(step.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Переконується, що лише гальмівні сигнали не активують нейрон навіть за нульового відсоткового порога. */
    @Test
    void inhibitorySignalsAloneCannotActivateTargetWithZeroPercentThreshold() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron inhibitory = model.createNeuron(NeuronType.INHIBITORY, 0, 0);
        Neuron target = countModeTarget(model, 0);
        assertTrue(model.createConnection(inhibitory.id(), target.id()));

        SimulationStep step = SimulationSession.manual(model, inhibitory.id()).nextStep();

        assertNotNull(step);
        assertEquals(-1, step.nextInputSums().get(target.id()));
        assertFalse(step.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Переконується, що лічильник створюється заново кожного такту, а не накопичує попередні сигнали. */
    @Test
    void signalCountDoesNotAccumulateAcrossTicks() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        source.setActiveTicks(2);
        Neuron inactiveSource = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron target = countModeTarget(model, 100);
        assertTrue(model.createConnection(source.id(), target.id()));
        assertTrue(model.createConnection(inactiveSource.id(), target.id()));
        SimulationSession session = SimulationSession.manual(model, source.id());

        SimulationStep first = session.nextStep();
        SimulationStep second = session.nextStep();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1, first.nextInputSums().get(target.id()));
        assertEquals(1, second.inputSums().get(target.id()));
        assertEquals(1, second.nextInputSums().get(target.id()));
        assertFalse(second.activatedNeuronIds().contains(target.id()));
        assertFalse(second.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Переконується, що в режимі сили сигналів величини сигналів і попередня умова активації не змінилися. */
    @Test
    void signalStrengthModeRetainsWeightedSignalBehavior() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron source = model.createNeuron(NeuronType.EXCITATORY, 0, 0);
        source.setSignalStrength(2);
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        target.setActivationThreshold(2);
        assertTrue(model.createConnection(source.id(), target.id()));

        SimulationStep step = SimulationSession.manual(model, source.id()).nextStep();

        assertNotNull(step);
        assertEquals(2, step.nextInputSums().get(target.id()));
        assertTrue(step.nextActivatedNeuronIds().contains(target.id()));
    }

    /** Створює нейрон призначення з відсотковим порогом для тестів поширення сигналів. */
    private static Neuron countModeTarget(NeuronMapModel model, int percentage) {
        Neuron target = model.createNeuron(NeuronType.EXCITATORY, 300, 0);
        target.setParameters(new NeuronParameters(1, percentage, 1, 1, NeuronActivationMode.SIGNAL_COUNT));
        return target;
    }

    /** Створює три збуджувальні входи, запускає задану кількість і перевіряє активацію за відсотковим порогом. */
    private static void assertActivation(int percentage, int activeSourceCount, boolean expectedActivation) {
        NeuronMapModel model = new NeuronMapModel();
        List<Neuron> sources = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            sources.add(model.createNeuron(NeuronType.EXCITATORY, index * 100.0, 0));
        }
        Neuron target = countModeTarget(model, percentage);
        for (Neuron source : sources) {
            assertTrue(model.createConnection(source.id(), target.id()));
        }

        SimulationSession session = SimulationSession.manual(model, sources.getFirst().id());
        for (int index = 1; index < activeSourceCount; index++) {
            assertTrue(session.queueManualStart(sources.get(index).id()));
        }

        SimulationStep step = session.nextStep();

        assertNotNull(step);
        assertEquals(activeSourceCount, step.nextInputSums().get(target.id()));
        assertEquals(expectedActivation, step.nextActivatedNeuronIds().contains(target.id()),
                "percentage=" + percentage + ", activeSourceCount=" + activeSourceCount);
    }
}
