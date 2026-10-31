package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє візуальне перенесення кінців зв'язків на закриті структури без зміни їхнього початкового кольору. */
final class ConnectionViewStructureMemberProxyTest {

    private static final double EPSILON = 0.001;

    /**
     * Перевіряє обидва напрямки між структурами, перемикання відкритої сторони та приховування при закритті обох.
     *
     * @throws Exception якщо JavaFX не може виконати перевірку у своєму потоці.
     */
    @Test
    void crossStructureConnectionsFollowVisibleDrawersAndKeepOriginalSourceColors() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                fixture.firstStructureView.toggleStructureDrawer();
                fixture.refreshConnections();

                assertTrue(fixture.forwardConnectionView.isVisible());
                assertTrue(fixture.reverseConnectionView.isVisible());
                assertConnectionStartsAt(fixture.forwardConnectionView, fixture.firstMemberView);
                assertConnectionTargets(fixture.forwardConnectionView,
                        fixture.secondStructureView, fixture.secondMemberView);
                assertConnectionStartsAt(fixture.reverseConnectionView, fixture.secondStructureView);
                assertConnectionTargets(fixture.reverseConnectionView,
                        fixture.firstMemberView, fixture.firstStructureView);
                assertOriginalSourceColor(fixture.forwardConnectionView, NeuronType.INHIBITORY);
                assertOriginalSourceColor(fixture.reverseConnectionView, NeuronType.EXCITATORY);

                fixture.firstStructureView.toggleStructureDrawer();
                fixture.secondStructureView.toggleStructureDrawer();
                fixture.refreshConnections();

                assertTrue(fixture.forwardConnectionView.isVisible());
                assertTrue(fixture.reverseConnectionView.isVisible());
                assertConnectionStartsAt(fixture.forwardConnectionView, fixture.firstStructureView);
                assertConnectionTargets(fixture.forwardConnectionView,
                        fixture.secondMemberView, fixture.secondStructureView);
                assertConnectionStartsAt(fixture.reverseConnectionView, fixture.secondMemberView);
                assertConnectionTargets(fixture.reverseConnectionView,
                        fixture.firstStructureView, fixture.firstMemberView);

                fixture.firstStructureView.toggleStructureDrawer();
                fixture.refreshConnections();
                assertConnectionStartsAt(fixture.forwardConnectionView, fixture.firstMemberView);
                assertConnectionTargets(fixture.forwardConnectionView,
                        fixture.secondMemberView, fixture.secondStructureView);
                assertConnectionStartsAt(fixture.reverseConnectionView, fixture.secondMemberView);
                assertConnectionTargets(fixture.reverseConnectionView,
                        fixture.firstMemberView, fixture.firstStructureView);
                assertOriginalSourceColor(fixture.forwardConnectionView, NeuronType.INHIBITORY);
                assertOriginalSourceColor(fixture.reverseConnectionView, NeuronType.EXCITATORY);

                fixture.firstStructureView.toggleStructureDrawer();
                fixture.secondStructureView.toggleStructureDrawer();
                fixture.refreshConnections();

                assertFalse(fixture.forwardConnectionView.isVisible(),
                        "Зв'язок має ховатися, коли обидва його структурні кінці приховані");
                assertFalse(fixture.reverseConnectionView.isVisible(),
                        "Приховування не повинно залежати від напрямку зв'язку");
                assertEquals(fixture.firstMember.id(), fixture.forwardConnectionView.model().sourceId());
                assertEquals(fixture.secondMember.id(), fixture.forwardConnectionView.model().targetId());
                assertEquals(fixture.secondMember.id(), fixture.reverseConnectionView.model().sourceId());
                assertEquals(fixture.firstMember.id(), fixture.reverseConnectionView.model().targetId());
                assertNull(fixture.forwardConnectionView.capturePulseSnapshot());
                assertNull(fixture.reverseConnectionView.capturePulseSnapshot());
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє, що вкладений нейрон переносить зв'язок на зовнішню структуру, якщо її шухлядка закрита.
     * Відкритість внутрішньої шухлядки не робить її вміст видимим крізь закритого зовнішнього предка.
     *
     * @throws Exception якщо JavaFX не може виконати перевірку у своєму потоці.
     */
    @Test
    void nestedMemberUsesOutermostClosedContainerAsVisibleEndpoint() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron outerStructure = model.createNeuron(NeuronType.STRUCTURE, 100.0, 90.0);
            Neuron innerStructure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
            Neuron nestedMember = model.createNeuron(NeuronType.INHIBITORY, 0.0, 0.0);
            Neuron destinationStructure = model.createNeuron(NeuronType.STRUCTURE, 650.0, 330.0);
            Neuron destinationMember = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
            WorkspaceView workspace = new WorkspaceView();
            NeuronView outerView = new NeuronView(model.presentation(outerStructure.id()));
            NeuronView innerView = new NeuronView(model.presentation(innerStructure.id()));
            NeuronView nestedMemberView = new NeuronView(model.presentation(nestedMember.id()));
            NeuronView destinationView = new NeuronView(model.presentation(destinationStructure.id()));
            NeuronView destinationMemberView = new NeuronView(model.presentation(destinationMember.id()));
            ConnectionView connectionView = null;
            try {
                assertTrue(model.addStructureMember(outerStructure.id(), innerStructure.id()));
                assertTrue(model.addStructureMember(innerStructure.id(), nestedMember.id()));
                assertTrue(model.addStructureMember(destinationStructure.id(), destinationMember.id()));
                assertTrue(model.createConnection(nestedMember.id(), destinationMember.id()));

                innerView.setStructureMembers(List.of(nestedMemberView));
                outerView.setStructureMembers(List.of(innerView));
                destinationView.setStructureMembers(List.of(destinationMemberView));
                workspace.nodeLayer().getChildren().addAll(outerView, destinationView);
                Scene scene = new Scene(workspace.node(), 1_000.0, 800.0);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                assertTrue(innerView.toggleStructureDrawer());
                assertTrue(destinationView.toggleStructureDrawer());
                Connection connection = model.connections().iterator().next();
                Map<String, NeuronView> views = Map.of(
                        outerStructure.id(), outerView,
                        innerStructure.id(), innerView,
                        nestedMember.id(), nestedMemberView,
                        destinationStructure.id(), destinationView,
                        destinationMember.id(), destinationMemberView);
                connectionView = new ConnectionView(connection, views::get, model::neuron);
                workspace.edgeLayer().getChildren().add(connectionView);
                connectionView.updateGeometry();

                assertTrue(connectionView.isVisible());
                assertFalse(outerView.isStructureDrawerExpanded());
                assertTrue(innerView.isStructureDrawerExpanded());
                assertConnectionStartsAt(connectionView, outerView);
                assertConnectionTargets(connectionView, destinationMemberView, destinationView);
                assertOriginalSourceColor(connectionView, NeuronType.INHIBITORY);
            } finally {
                if (connectionView != null) {
                    connectionView.stopDeleteHighlight();
                }
                List.of(outerView, innerView, nestedMemberView, destinationView, destinationMemberView)
                        .forEach(NeuronView::disposeParameterTooltip);
                workspace.dispose();
            }
        });
    }


    /**
     * Перевіряє згортання кількох зв'язків у одну лінію зі збереженням окремого імпульсу кожного джерела.
     * Відкриття структури повертає окремі лінії, а закриття обох сторін знову приховує їх.
     *
     * @throws Exception якщо JavaFX не може виконати перевірку у своєму потоці.
     */
    @Test
    void severalHiddenMembersSharingOneVisibleTargetUseOneLineAndKeepIndependentPulses() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            Neuron sourceStructure = model.createNeuron(NeuronType.STRUCTURE, 100.0, 90.0);
            Neuron targetStructure = model.createNeuron(NeuronType.STRUCTURE, 680.0, 340.0);
            Neuron firstSource = model.createNeuron(NeuronType.INHIBITORY, 0.0, 0.0);
            Neuron secondSource = model.createNeuron(NeuronType.INHIBITORY, 0.0, 0.0);
            Neuron targetMember = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
            WorkspaceView workspace = new WorkspaceView();
            Map<String, NeuronView> views = new HashMap<>();
            List<NeuronView> allViews = List.of(
                    createView(model, sourceStructure.id()),
                    createView(model, targetStructure.id()),
                    createView(model, firstSource.id()),
                    createView(model, secondSource.id()),
                    createView(model, targetMember.id()));
            NeuronView sourceStructureView = allViews.get(0);
            NeuronView targetStructureView = allViews.get(1);
            NeuronView firstSourceView = allViews.get(2);
            NeuronView secondSourceView = allViews.get(3);
            NeuronView targetMemberView = allViews.get(4);
            List<ConnectionView> connections = new java.util.ArrayList<>();
            PulseAnimationController pulses = new PulseAnimationController(workspace);
            try {
                assertTrue(model.addStructureMember(sourceStructure.id(), firstSource.id()));
                assertTrue(model.addStructureMember(sourceStructure.id(), secondSource.id()));
                assertTrue(model.addStructureMember(targetStructure.id(), targetMember.id()));
                assertTrue(model.createConnection(firstSource.id(), targetMember.id()));
                assertTrue(model.createConnection(secondSource.id(), targetMember.id()));

                views.put(sourceStructure.id(), sourceStructureView);
                views.put(targetStructure.id(), targetStructureView);
                views.put(firstSource.id(), firstSourceView);
                views.put(secondSource.id(), secondSourceView);
                views.put(targetMember.id(), targetMemberView);
                sourceStructureView.setStructureMembers(List.of(firstSourceView, secondSourceView));
                targetStructureView.setStructureMembers(List.of(targetMemberView));
                workspace.nodeLayer().getChildren().addAll(sourceStructureView, targetStructureView);

                Scene scene = new Scene(workspace.node(), 1_000.0, 800.0);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                assertTrue(targetStructureView.toggleStructureDrawer());

                for (Connection connection : model.connections()) {
                    ConnectionView view = new ConnectionView(connection, views::get, model::neuron);
                    connections.add(view);
                    workspace.edgeLayer().getChildren().add(view);
                }
                connections.forEach(ConnectionView::updateGeometry);

                assertEquals(1, connections.stream().filter(ConnectionView::isVisualRepresentative).count(),
                        "для спільної видимої пари портів має бути лише один представник");
                assertEquals(1, visibleConnectionLineCount(connections),
                        "друга логічна лінія не повинна малюватися поверх першої");
                assertEquals(1, visibleConnectionArrowCount(connections),
                        "для спільного видимого зв'язку має бути лише один наконечник");
                assertTrue(connections.stream().allMatch(ConnectionView::isVisible),
                        "логічні подання мають залишатися доступними для маршрутизації сигналів");
                for (ConnectionView connection : connections) {
                    if (connection.isVisualRepresentative()) {
                        assertConnectionStartsAt(connection, sourceStructureView);
                        assertConnectionTargets(connection, targetMemberView, targetStructureView);
                    }
                    assertNotNull(connection.capturePulseSnapshot(),
                            "кожен реальний зв'язок має отримувати знімок спільної видимої геометрії");
                }

                ConnectionView firstConnection = connections.stream()
                        .filter(view -> view.model().sourceId().equals(firstSource.id())).findFirst().orElseThrow();
                ConnectionView secondConnection = connections.stream()
                        .filter(view -> view.model().sourceId().equals(secondSource.id())).findFirst().orElseThrow();
                assertEquals(firstConnection.capturePulseSnapshot().start(), secondConnection.capturePulseSnapshot().start());
                assertEquals(firstConnection.capturePulseSnapshot().end(), secondConnection.capturePulseSnapshot().end());
                assertEquals(ConnectionStyle.DEFAULT.pulseColor(model.neuron(firstSource.id()).type()),
                        firstConnection.capturePulseSnapshot().pulseColor());
                assertEquals(ConnectionStyle.DEFAULT.pulseColor(model.neuron(secondSource.id()).type()),
                        secondConnection.capturePulseSnapshot().pulseColor());

                pulses.play(firstConnection);
                pulses.play(secondConnection);
                assertEquals(2, workspace.pulseLayer().getChildren().size(),
                        "кожен сигнал має створити власний трикутник на спільній лінії");
                pulses.stopAll();

                assertTrue(sourceStructureView.toggleStructureDrawer());
                connections.forEach(ConnectionView::updateGeometry);
                assertEquals(2, connections.stream().filter(ConnectionView::isVisualRepresentative).count(),
                        "після відкриття джерельної структури кожен учасник стає окремим кінцем");
                assertEquals(2, visibleConnectionLineCount(connections));
                assertEquals(2, visibleConnectionArrowCount(connections));

                assertFalse(sourceStructureView.toggleStructureDrawer());
                connections.forEach(ConnectionView::updateGeometry);
                assertEquals(1, visibleConnectionLineCount(connections));

                assertFalse(targetStructureView.toggleStructureDrawer());
                connections.forEach(ConnectionView::updateGeometry);
                assertTrue(connections.stream().noneMatch(ConnectionView::isVisible),
                        "зв'язки мають ховатися, коли обидва кінці кожного зв'язку приховані");
                assertTrue(connections.stream().allMatch(view -> view.capturePulseSnapshot() == null));
            } finally {
                pulses.stopAll();
                connections.forEach(ConnectionView::stopDeleteHighlight);
                allViews.forEach(NeuronView::disposeParameterTooltip);
                workspace.dispose();
            }
        });
    }

    /** Перевіряє, що початок лінії збігається з вихідним портом потрібного візуального нейрона. */
    private static void assertConnectionStartsAt(ConnectionView connection, NeuronView expectedSource) {
        Point2D expected = connection.sceneToLocal(expectedSource.outputPointInScene());
        Line line = visibleLine(connection);
        assertEquals(expected.getX(), line.getStartX(), EPSILON);
        assertEquals(expected.getY(), line.getStartY(), EPSILON);
    }

    /**
     * Перевіряє, що вістря стрілки розташоване біля очікуваного вхідного порта, а не біля прихованого учасника.
     *
     * @param connection подання напрямленого зв'язку.
     * @param expectedTarget нейрон або контейнер, до якого має вести стрілка.
     * @param alternativeTarget інший кандидат, який не повинен бути візуальним кінцем.
     */
    private static void assertConnectionTargets(
            ConnectionView connection,
            NeuronView expectedTarget,
            NeuronView alternativeTarget
    ) {
        Line hitLine = hitLine(connection);
        Point2D arrowTip = new Point2D(hitLine.getEndX(), hitLine.getEndY());
        Point2D expectedPort = connection.sceneToLocal(expectedTarget.inputPointInScene());
        Point2D alternativePort = connection.sceneToLocal(alternativeTarget.inputPointInScene());

        assertTrue(arrowTip.distance(expectedPort) < arrowTip.distance(alternativePort),
                "Вістря стрілки має бути біля видимого контейнера або відкритого учасника");
    }

    /** Перевіряє, що графічна палітра зв'язку залежить від типу справжнього нейрона-джерела.
     * @param connection подання напрямленого зв'язку.
     * @param sourceType фактичний тип нейрона, з якого починається зв'язок у моделі.
     */
    private static void assertOriginalSourceColor(ConnectionView connection, NeuronType sourceType) {
        Color expectedColor = ConnectionStyle.DEFAULT.sourceColor(sourceType);
        assertEquals(expectedColor, visibleLine(connection).getStroke());
        assertEquals(expectedColor, ((Polygon) connection.getChildren().get(2)).getFill());
        var pulseSnapshot = connection.capturePulseSnapshot();
        assertNotNull(pulseSnapshot);
        assertEquals(ConnectionStyle.DEFAULT.pulseColor(sourceType), pulseSnapshot.pulseColor());
    }


    /** Підраховує кількість видимих трикутних наконечників, що відповідають намальованим лініям. */
    private static long visibleConnectionArrowCount(List<ConnectionView> connections) {
        return connections.stream()
                .map(ConnectionView::getChildren)
                .filter(children -> children.get(2).isVisible())
                .count();
    }

    /** Створює візуальне подання заданого нейрона моделі. */
    private static NeuronView createView(NeuronMapModel model, String neuronId) {
        return new NeuronView(model.presentation(neuronId));
    }

    /** Підраховує лише графічні лінії, які реально малюються, а не логічні зв'язки-делегати. */
    private static long visibleConnectionLineCount(List<ConnectionView> connections) {
        return connections.stream()
                .map(ConnectionView::getChildren)
                .filter(children -> children.getFirst().isVisible())
                .count();
    }

    /** Повертає видимий стрижень зв'язку для перевірки його геометрії та кольору.
     * @param connection подання напрямленого зв'язку.
     * @return графічна лінія зв'язку.
     */
    private static Line visibleLine(ConnectionView connection) {
        return (Line) connection.getChildren().getFirst();
    }

    /** Повертає прозору лінію вибору, кінцева точка якої збігається з вістрям стрілки.
     * @param connection подання напрямленого зв'язку.
     * @return прозора графічна лінія для hit-testing.
     */
    private static Line hitLine(ConnectionView connection) {
        return (Line) connection.getChildren().get(1);
    }

    /** Створює дві незалежні структури з учасниками різних типів та зв'язками в обох напрямках. */
    private static final class Fixture {
        private final NeuronMapModel model = new NeuronMapModel();
        private final Neuron firstStructure = model.createNeuron(NeuronType.STRUCTURE, 100.0, 90.0);
        private final Neuron secondStructure = model.createNeuron(NeuronType.STRUCTURE, 650.0, 330.0);
        private final Neuron firstMember = model.createNeuron(NeuronType.INHIBITORY, 0.0, 0.0);
        private final Neuron secondMember = model.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final NeuronView firstStructureView = createView(firstStructure.id());
        private final NeuronView secondStructureView = createView(secondStructure.id());
        private final NeuronView firstMemberView = createView(firstMember.id());
        private final NeuronView secondMemberView = createView(secondMember.id());
        private final ConnectionView forwardConnectionView;
        private final ConnectionView reverseConnectionView;

        /** Ініціалізує дерево JavaFX, членство й два напрямлені подання зв'язків. */
        private Fixture() {
            if (!model.addStructureMember(firstStructure.id(), firstMember.id())
                    || !model.addStructureMember(secondStructure.id(), secondMember.id())
                    || !model.createConnection(firstMember.id(), secondMember.id())
                    || !model.createConnection(secondMember.id(), firstMember.id())) {
                throw new IllegalStateException("Не вдалося підготувати тестову карту структур");
            }

            neuronViews.put(firstStructure.id(), firstStructureView);
            neuronViews.put(secondStructure.id(), secondStructureView);
            neuronViews.put(firstMember.id(), firstMemberView);
            neuronViews.put(secondMember.id(), secondMemberView);
            firstStructureView.setStructureMembers(List.of(firstMemberView));
            secondStructureView.setStructureMembers(List.of(secondMemberView));
            workspace.nodeLayer().getChildren().addAll(firstStructureView, secondStructureView);

            Scene scene = new Scene(workspace.node(), 1_000.0, 800.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Connection forward = findConnection(firstMember.id(), secondMember.id());
            Connection reverse = findConnection(secondMember.id(), firstMember.id());
            forwardConnectionView = new ConnectionView(forward, neuronViews::get, model::neuron);
            reverseConnectionView = new ConnectionView(reverse, neuronViews::get, model::neuron);
            workspace.edgeLayer().getChildren().addAll(forwardConnectionView, reverseConnectionView);
        }

        /** Знаходить напрямлений зв'язок за його незмінними ідентифікаторами кінців. */
        private Connection findConnection(String sourceId, String targetId) {
            return model.connections().stream()
                    .filter(connection -> connection.sourceId().equals(sourceId)
                            && connection.targetId().equals(targetId))
                    .findFirst()
                    .orElseThrow();
        }

        /** Перераховує геометрію обох зв'язків після зміни стану шухлядок. */
        private void refreshConnections() {
            forwardConnectionView.updateGeometry();
            reverseConnectionView.updateGeometry();
        }

        /** Звільняє таймлайни зв'язків, підказки нейронів і ресурси робочої області. */
        private void dispose() {
            forwardConnectionView.stopDeleteHighlight();
            reverseConnectionView.stopDeleteHighlight();
            neuronViews.values().forEach(NeuronView::disposeParameterTooltip);
            workspace.dispose();
        }

        /** Створює JavaFX-подання з централізованого подання нейрона, збереженого в моделі карти. */
        private NeuronView createView(String neuronId) {
            return new NeuronView(model.presentation(neuronId));
        }
    }
}
