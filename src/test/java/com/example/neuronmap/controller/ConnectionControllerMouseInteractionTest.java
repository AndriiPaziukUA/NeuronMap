package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє кнопки скасування режимів зв’язків і сумісність цих режимів із панорамуванням полотна. */
final class ConnectionControllerMouseInteractionTest {

    /** Перевіряє, що ліве перетягування полотна не вимикає режим створення зв’язків. */
    @Test
    void draggingCanvasKeepsConnectionCreationActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(false);
            try {
                fixture.connectionController().beginCreate(fixture.source().model().id());
                installControllers(fixture);

                dragCanvas(fixture, 100.0, 90.0, 132.0, 116.0);

                assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
                assertEquals(fixture.source().model().id(), fixture.state().connectionSourceId());
                assertEquals(32.0, fixture.state().panX(), 0.0001);
                assertEquals(26.0, fixture.state().panY(), 0.0001);
                assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /** Перевіряє, що кінець прев’ю продовжує слідувати за курсором під час панорамування. */
    @Test
    void draggingCanvasKeepsConnectionPreviewAtCurrentPointerPosition() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(false);
            try {
                fixture.connectionController().beginCreate(fixture.source().model().id());
                installControllers(fixture);
                Line previewLine = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();

                dragCanvas(fixture, 100.0, 90.0, 132.0, 116.0);

                assertEquals(132.0, previewLine.getEndX(), 0.0001);
                assertEquals(116.0, previewLine.getEndY(), 0.0001);
                assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /** Перевіряє, що ліве перетягування полотна не вимикає режим видалення зв’язків. */
    @Test
    void draggingCanvasKeepsConnectionDeletionActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(true);
            try {
                fixture.connectionController().beginDelete(fixture.source().model().id());
                installControllers(fixture);

                dragCanvas(fixture, 80.0, 70.0, 105.0, 99.0);

                assertEquals(EditorState.Mode.DELETE_CONNECTION, fixture.state().mode());
                assertEquals(fixture.source().model().id(), fixture.state().deleteConnectionNeuronId());
                assertEquals(25.0, fixture.state().panX(), 0.0001);
                assertEquals(29.0, fixture.state().panY(), 0.0001);
                assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /** Перевіряє, що лівий клік по фону полотна не скасовує режим створення зв’язків. */
    @Test
    void leftClickOnBlankCanvasKeepsConnectionCreationActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(false);
            try {
                fixture.connectionController().beginCreate(fixture.source().model().id());
                installControllers(fixture);

                fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_CLICKED,
                        MouseButton.PRIMARY, 250.0, 240.0, false);

                assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
                assertEquals(fixture.source().model().id(), fixture.state().connectionSourceId());
                assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /** Перевіряє, що права кнопка скасовує створення зв’язків і прибирає прев’ю. */
    @Test
    void rightClickCancelsConnectionCreation() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(false);
            try {
                fixture.connectionController().beginCreate(fixture.source().model().id());
                installControllers(fixture);

                fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_CLICKED,
                        MouseButton.SECONDARY, 250.0, 240.0, false);

                assertEquals(EditorState.Mode.IDLE, fixture.state().mode());
                assertNull(fixture.state().connectionSourceId());
                assertTrue(fixture.workspace().overlayLayer().getChildren().isEmpty());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /** Перевіряє, що права кнопка завершує видалення зв’язків і очищує ідентифікатор джерела. */
    @Test
    void rightClickExitsConnectionDeletion() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture(true);
            try {
                fixture.connectionController().beginDelete(fixture.source().model().id());
                installControllers(fixture);

                fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_CLICKED,
                        MouseButton.SECONDARY, 250.0, 240.0, false);

                assertEquals(EditorState.Mode.IDLE, fixture.state().mode());
                assertNull(fixture.state().deleteConnectionNeuronId());
            } finally {
                fixture.disposeControllers();
            }
        });
    }

    /**
     * Створює полотно, контролери й два нейрони, за потреби додаючи зв’язок для режиму видалення.
     *
     * @param withConnection чи потрібно створити зв’язок між тестовими нейронами.
     * @return ізольовані залежності сценарію перевірки взаємодії миші.
     */
    private static Fixture createFixture(boolean withConnection) {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceModel = model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
        Neuron targetModel = model.createNeuron(NeuronType.INHIBITORY, 240.0, 160.0);
        NeuronView source = new NeuronView(model.presentation(sourceModel.id()));
        NeuronView target = new NeuronView(model.presentation(targetModel.id()));
        WorkspaceView workspace = new WorkspaceView();
        workspace.nodeLayer().getChildren().addAll(source, target);
        Scene scene = new Scene(new StackPane(workspace.node()), 640.0, 480.0);
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        EditorState state = new EditorState(1.0, 0.0, 0.0);
        ConnectionService service = new ConnectionService(model);
        if (withConnection) {
            service.create(sourceModel.id(), targetModel.id());
        }
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        ConnectionController connectionController = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        service,
                        state,
                        workspace,
                        new HashMap<>(Map.of(sourceModel.id(), source, targetModel.id(), target)),
                        () -> { },
                        () -> { },
                        ignored -> { },
                        localization
                )
        );
        CameraController cameraController = new CameraController(
                state,
                workspace,
                connectionController::refreshPreviewAfterCameraChange,
                () -> { },
                state::isSpecialModeActive,
                ignored -> false,
                ignored -> { },
                ignored -> { },
                1.2,
                localization
        );

        return new Fixture(
                workspace,
                state,
                source,
                connectionController,
                cameraController
        );
    }

    /** Підключає обробники контролерів після додавання полотна до сцени. */
    private static void installControllers(Fixture fixture) {
        fixture.connectionController().install();
        fixture.cameraController().install();
    }

    /** Виконує перетягування полотна через події JavaFX, включно з натисканням, рухом та відпусканням. */
    private static void dragCanvas(
            Fixture fixture,
            double startX,
            double startY,
            double endX,
            double endY
    ) {
        fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_PRESSED,
                MouseButton.PRIMARY, startX, startY, true);
        fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_DRAGGED,
                MouseButton.PRIMARY, endX, endY, true);
        fireMouseEvent(fixture.workspace(), MouseEvent.MOUSE_RELEASED,
                MouseButton.PRIMARY, endX, endY, false);
    }

    /** Надсилає подію миші до кореневого вузла полотна з узгодженим станом натиснутої кнопки. */
    private static void fireMouseEvent(
            WorkspaceView workspace,
            EventType<MouseEvent> eventType,
            MouseButton button,
            double x,
            double y,
            boolean buttonDown
    ) {
        boolean pressEvent = eventType == MouseEvent.MOUSE_PRESSED;
        boolean releaseEvent = eventType == MouseEvent.MOUSE_RELEASED;
        MouseEvent event = new MouseEvent(
                eventType,
                x,
                y,
                x,
                y,
                button,
                1,
                false,
                false,
                false,
                false,
                buttonDown && button == MouseButton.PRIMARY,
                buttonDown && button == MouseButton.MIDDLE,
                buttonDown && button == MouseButton.SECONDARY,
                false,
                false,
                pressEvent || (!releaseEvent && buttonDown),
                null
        );
        Event.fireEvent(workspace.node(), event);
    }

    /**
     * Зберігає склад залежностей тесту, не повторюючи створення контролерів та полотна.
     *
     * @param workspace полотно тестового редактора.
     * @param state спільний стан режимів і камери.
     * @param source початковий нейрон тестової карти.
     * @param connectionController контролер режимів зв’язків.
     * @param cameraController контролер панорамування.
     */
    private record Fixture(
            WorkspaceView workspace,
            EditorState state,
            NeuronView source,
            ConnectionController connectionController,
            CameraController cameraController
    ) {
        /** Звільняє фільтри й таймер відкладеного збереження після завершення сценарію. */
        private void disposeControllers() {
            connectionController.dispose();
            cameraController.dispose();
        }
    }
}
