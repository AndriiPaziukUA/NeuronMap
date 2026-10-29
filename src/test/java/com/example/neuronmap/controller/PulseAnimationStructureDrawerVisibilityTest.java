package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import javafx.scene.Scene;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє синхронізацію видимості імпульсів із відкриттям і закриттям шухлядок структур. */
class PulseAnimationStructureDrawerVisibilityTest {

    /**
     * Перевіряє, що імпульс ховається разом із закритою шухлядкою та повертається на поточному прогресі.
     *
     * @throws Exception якщо JavaFX не запускається або не завершує синхронну тестову дію.
     */
    @Test
    void activePulseFollowsDrawerVisibilityAndKeepsProgress() throws Exception {
        AtomicReference<Fixture> fixtureReference = new AtomicReference<>();
        AtomicReference<Double> initialTipXReference = new AtomicReference<>();
        AtomicReference<Double> hiddenTipXReference = new AtomicReference<>();

        try {
            JavaFxTestSupport.runAndWait(() -> {
                Fixture fixture = new Fixture();
                fixtureReference.set(fixture);
                fixture.startPulse();
                initialTipXReference.set(fixture.tipX());

                fixture.structureView.toggleStructureDrawer();
                fixture.connectionView.updateGeometry();

                assertFalse(fixture.connectionView.isVisible(), "Зв'язок до учасника закритої шухлядки має ховатися");
                assertFalse(fixture.pulseView.isVisible(), "Імпульс має ховатися разом із прихованим зв'язком");
                assertFalse(fixture.pulseView.isFinished(), "Приховування не повинно зупиняти часову шкалу імпульсу");
                assertEquals(1, fixture.pulseController.activeAnimationCountForTest());
                assertTrue(fixture.workspace.pulseLayer().getChildren().contains(fixture.pulseView));
            });

            Thread.sleep(180L);

            JavaFxTestSupport.runAndWait(() -> {
                Fixture fixture = fixtureReference.get();
                assertNotNull(fixture);
                assertFalse(fixture.pulseView.isVisible(), "Імпульс має залишатися прихованим, поки шухлядка закрита");
                assertFalse(fixture.pulseView.isFinished(), "Імпульс має продовжувати рух у прихованому стані");
                assertEquals(1, fixture.pulseController.activeAnimationCountForTest());
                double hiddenTipX = fixture.tipX();
                hiddenTipXReference.set(hiddenTipX);
                assertTrue(
                        Math.abs(hiddenTipX - initialTipXReference.get()) > 1.0,
                        "Прогрес імпульсу має змінитися, поки його не видно"
                );

                fixture.structureView.toggleStructureDrawer();
                fixture.connectionView.updateGeometry();

                assertTrue(fixture.connectionView.isVisible(), "Зв'язок має відновитися після відкриття шухлядки");
                assertTrue(fixture.pulseView.isVisible(), "Активний імпульс має відразу з'явитися після відкриття шухлядки");
                assertFalse(fixture.pulseView.isFinished());
                assertEquals(1, fixture.pulseController.activeAnimationCountForTest());
                assertEquals(1, fixture.workspace.pulseLayer().getChildren().size());
                assertEquals(
                        hiddenTipXReference.get(),
                        fixture.tipX(),
                        0.001,
                        "Після відкриття потрібно показати актуальну позицію, не перезапускаючи імпульс"
                );
            });
        } finally {
            JavaFxTestSupport.runAndWait(() -> {
                Fixture fixture = fixtureReference.get();
                if (fixture != null) {
                    fixture.dispose();
                }
            });
        }
    }

    /** Збирає мінімальне реальне дерево JavaFX зі структурою, її учасником і вхідним для нього зв'язком. */
    private static final class Fixture {
        private final NeuronMapModel model = new NeuronMapModel();
        private final Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 80.0, 60.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final NeuronView structureView = new NeuronView(model.presentation(structure.id()));
        private final ConnectionView connectionView;
        private final PulseAnimationController pulseController = new PulseAnimationController(workspace);
        private final PulseAnimationView pulseView;

        /** Створює зв'язок і розміщує всі графічні елементи в одній сцені. */
        private Fixture() {
            Neuron member = model.createNeuron(NeuronType.EXCITATORY, 120.0, 110.0);
            Neuron source = model.createNeuron(NeuronType.EXCITATORY, 500.0, 100.0);
            assertTrue(model.addStructureMember(structure.id(), member.id()));
            assertTrue(model.createConnection(source.id(), member.id()));
            Connection connection = model.connections().iterator().next();

            new Scene(workspace.node(), 800.0, 600.0);
            Map<String, NeuronView> neuronViews = new HashMap<>();
            NeuronView memberView = new NeuronView(model.presentation(member.id()));
            NeuronView sourceView = new NeuronView(model.presentation(source.id()));
            neuronViews.put(structure.id(), structureView);
            neuronViews.put(member.id(), memberView);
            neuronViews.put(source.id(), sourceView);
            structureView.setStructureMembers(List.of(memberView));
            structureView.toggleStructureDrawer();
            workspace.nodeLayer().getChildren().addAll(structureView, sourceView);

            connectionView = new ConnectionView(connection, neuronViews::get, model::neuron);
            workspace.edgeLayer().getChildren().add(connectionView);
            connectionView.updateGeometry();
            assertTrue(connectionView.isVisible(), "Відкрита шухлядка має показувати зв'язок із її учасником");

            pulseController.setTickDurationMillis(1_800.0);
            pulseController.play(connectionView);
            pulseView = (PulseAnimationView) workspace.pulseLayer().getChildren().getFirst();
        }

        /** Запускає підготовлену анімацію та перевіряє, що початковий імпульс існує. */
        private void startPulse() {
            assertNotNull(pulseView);
            assertTrue(pulseView.isVisible());
            assertFalse(pulseView.isFinished());
        }

        /** Повертає горизонтальну координату вершини трикутника для перевірки прогресу руху. */
        private double tipX() {
            Polygon arrow = (Polygon) pulseView.getChildren().getFirst();
            return arrow.getPoints().getFirst();
        }

        /** Завершує анімацію та звільняє ресурси тестової робочої області. */
        private void dispose() {
            pulseController.stopAll();
            workspace.dispose();
        }
    }
}
