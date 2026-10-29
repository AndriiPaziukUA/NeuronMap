package com.example.neuronmap.view.simulation;

import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє синхронізацію імпульсів і відображення агрегованих індикаторів структур. */
final class SimulationStepPresenterPulseSynchronizationTest {

    /**
     * Перевіряє завершення вхідного імпульсу перед запуском вихідного імпульсу активованого нейрона.
     *
     * @throws Exception якщо виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void outgoingPulseCanBeRepeatedAfterPriorTickAnimationsAreCompleted() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron first = model.createNeuron(NeuronType.EXCITATORY, 50.0, 100.0);
            Neuron second = model.createNeuron(NeuronType.EXCITATORY, 250.0, 100.0);
            Neuron third = model.createNeuron(NeuronType.EXCITATORY, 450.0, 100.0);
            assertTrue(model.createConnection(first.id(), second.id()));
            assertTrue(model.createConnection(second.id(), third.id()));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> neuronViews = new LinkedHashMap<>();
            for (Neuron neuron : model.neurons()) {
                NeuronView neuronView = new NeuronView(model.presentation(neuron.id()));
                neuronViews.put(neuron.id(), neuronView);
                workspace.nodeLayer().getChildren().add(neuronView);
            }
            for (Connection connection : model.connections()) {
                ConnectionView connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
                workspace.edgeLayer().getChildren().add(connectionView);
            }

            PulseAnimationController pulseController = new PulseAnimationController(workspace);
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                    pulseController
            );
            List<PulseAnimationView> previousPulses;
            try {
                presenter.apply(new SimulationStep(
                        BigInteger.ZERO,
                        Map.of(second.id(), first.signalWeight()),
                        Set.of(second.id()),
                        Set.of(first.id(), second.id()),
                        Map.of(second.id(), first.signalWeight(), third.id(), second.signalWeight()),
                        Map.of(),
                        Set.of(second.id(), third.id())
                ));
                assertEquals(2, workspace.pulseLayer().getChildren().size());
                previousPulses = workspace.pulseLayer().getChildren().stream()
                        .map(PulseAnimationView.class::cast)
                        .toList();

                presenter.completeAnimationsAtTickBoundary();

                assertTrue(previousPulses.stream().allMatch(PulseAnimationView::isFinished));
                assertTrue(workspace.pulseLayer().getChildren().isEmpty());
                for (PulseAnimationView previousPulse : previousPulses) {
                    Polygon arrivedArrow = (Polygon) previousPulse.getChildren().getFirst();
                    double tipX = arrivedArrow.getPoints().getFirst();
                    double tipY = arrivedArrow.getPoints().get(1);
                    boolean reachedSecond = Math.abs(neuronViews.get(second.id()).inputPoint().getX() - tipX) < 0.001
                            && Math.abs(neuronViews.get(second.id()).inputPoint().getY() - tipY) < 0.001;
                    boolean reachedThird = Math.abs(neuronViews.get(third.id()).inputPoint().getX() - tipX) < 0.001
                            && Math.abs(neuronViews.get(third.id()).inputPoint().getY() - tipY) < 0.001;
                    assertTrue(reachedSecond || reachedThird);
                }

                presenter.apply(new SimulationStep(
                        BigInteger.ONE,
                        Map.of(second.id(), first.signalWeight()),
                        Set.of(second.id()),
                        Set.of(second.id()),
                        Map.of(third.id(), second.signalWeight()),
                        Map.of(),
                        Set.of(third.id())
                ));

                assertEquals(1, workspace.pulseLayer().getChildren().size());
                PulseAnimationView nextPulse = (PulseAnimationView) workspace.pulseLayer().getChildren().getFirst();
                assertTrue(previousPulses.stream().noneMatch(previousPulse -> previousPulse == nextPulse));
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }

    /** Перевіряє числа структур і анімацію кожного зв'язку до структури, для якої створено спільний центр. */
    @Test
    void structureNumbersAggregateMemberActivityAndAnimateEachLinkedTargetCenter() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron sourceStructure = model.createNeuron(NeuronType.STRUCTURE, 50.0, 80.0);
            Neuron targetStructure = model.createNeuron(NeuronType.STRUCTURE, 250.0, 80.0);
            Neuron idleStructure = model.createNeuron(NeuronType.STRUCTURE, 450.0, 80.0);
            Neuron sourceMember = model.createNeuron(NeuronType.EXCITATORY, 50.0, 180.0);
            Neuron targetMember = model.createNeuron(NeuronType.EXCITATORY, 250.0, 180.0);
            Neuron idleMember = model.createNeuron(NeuronType.EXCITATORY, 450.0, 180.0);
            assertTrue(model.addStructureMember(sourceStructure.id(), sourceMember.id()));
            assertTrue(model.addStructureMember(targetStructure.id(), targetMember.id()));
            assertTrue(model.addStructureMember(idleStructure.id(), idleMember.id()));
            targetMember.setActivationThreshold(1);
            assertTrue(model.createConnection(sourceMember.id(), targetMember.id()));
            assertTrue(model.createConnection(sourceStructure.id(), targetStructure.id()));
            assertTrue(model.createConnection(sourceStructure.id(), idleStructure.id()));

            Map<String, NeuronView> neuronViews = new LinkedHashMap<>();
            for (Neuron neuron : model.neurons()) {
                neuronViews.put(neuron.id(), new NeuronView(model.presentation(neuron.id())));
            }
            neuronViews.get(sourceStructure.id()).setStructureMembers(List.of(neuronViews.get(sourceMember.id())));
            neuronViews.get(targetStructure.id()).setStructureMembers(List.of(neuronViews.get(targetMember.id())));
            neuronViews.get(idleStructure.id()).setStructureMembers(List.of(neuronViews.get(idleMember.id())));

            WorkspaceView workspace = new WorkspaceView();
            new Scene(workspace.node(), 800.0, 500.0);
            workspace.nodeLayer().getChildren().addAll(List.of(
                    neuronViews.get(sourceStructure.id()),
                    neuronViews.get(targetStructure.id()),
                    neuronViews.get(idleStructure.id())
            ));
            RecordingPulseAnimator pulseAnimator = new RecordingPulseAnimator();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    neuronViews,
                    () -> neuronViews.values().forEach(view -> view.refreshVisuals(false)),
                    pulseAnimator
            );

            String activeStructureConnectionId = null;
            String secondTargetStructureConnectionId = null;
            for (Connection connection : model.connections()) {
                if (model.structure(connection.sourceId()) == null || model.structure(connection.targetId()) == null) {
                    continue;
                }
                ConnectionView connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
                workspace.edgeLayer().getChildren().add(connectionView);
                if (connection.targetId().equals(targetStructure.id())) {
                    activeStructureConnectionId = connection.id();
                } else if (connection.targetId().equals(idleStructure.id())) {
                    secondTargetStructureConnectionId = connection.id();
                }
            }

            try {
                SimulationStep step = SimulationSession.manual(model, sourceMember.id()).nextStep();
                assertNotNull(step);
                presenter.apply(step);

                assertEquals("0", ((Label) neuronViews.get(sourceStructure.id()).getChildren().get(1)).getText());
                assertEquals("1", ((Label) neuronViews.get(sourceStructure.id()).getChildren().get(2)).getText());
                assertEquals("1", ((Label) neuronViews.get(targetStructure.id()).getChildren().get(1)).getText());
                assertEquals("0", ((Label) neuronViews.get(targetStructure.id()).getChildren().get(2)).getText());
                assertEquals("1", ((Label) neuronViews.get(idleStructure.id()).getChildren().get(1)).getText(),
                        "структура-приймач із власним центром також отримує сигнал");
                assertEquals("0", ((Label) neuronViews.get(idleStructure.id()).getChildren().get(2)).getText());
                Circle targetInputPort = (Circle) neuronViews.get(targetMember.id()).getChildren().get(3);
                assertTrue(targetInputPort.getStyleClass().contains("port-input-sufficient"));

                assertNotNull(activeStructureConnectionId);
                assertNotNull(secondTargetStructureConnectionId);
                assertTrue(pulseAnimator.playedConnectionIds.contains(activeStructureConnectionId));
                assertTrue(pulseAnimator.playedConnectionIds.contains(secondTargetStructureConnectionId));
                assertEquals(Set.of(activeStructureConnectionId, secondTargetStructureConnectionId),
                        step.structureConnectionIdsToAnimate());
            } finally {
                presenter.clearRuntime();
                workspace.dispose();
            }
        });
    }

    /** Записує ID зв'язків, анімацію яких запитало подання, щоб перевірити маршрутизацію без часових шкал JavaFX. */
    private static final class RecordingPulseAnimator implements PulseAnimator {
        private final List<String> playedConnectionIds = new ArrayList<>();

        /** Призупинення не потрібне для записувального тестового компонента. */
        @Override
        public void pauseAll() { }

        /** Відновлення не потрібне для записувального тестового компонента. */
        @Override
        public void resumeAll() { }

        /** Очищує записані запити анімації. */
        @Override
        public void stopAll() { playedConnectionIds.clear(); }

        /** Немає активних зв'язків, які потребують зупинки. */
        @Override
        public void stopForNeuron(String neuronId) { }

        /** Повертає, чи є незавершена тестова анімація. */
        @Override
        public boolean hasActiveAnimations() { return false; }

        /** Записує ID зв'язку, для якого подання запустило анімацію. */
        @Override
        public void play(ConnectionView connectionView) {
            playedConnectionIds.add(connectionView.model().id());
        }

        /** Записує ID зв'язку й перевіряє коректність спеціальної тривалості. */
        @Override
        public void play(ConnectionView connectionView, double durationMillis) {
            if (!Double.isFinite(durationMillis) || durationMillis <= 0.0) {
                throw new IllegalArgumentException("animation duration must be finite and positive");
            }
            play(connectionView);
        }

        /** Тестовий компонент не керує реальними часовими шкалами JavaFX. */
        @Override
        public void setTickDurationMillis(double tickDurationMillis) { }
    }
}
