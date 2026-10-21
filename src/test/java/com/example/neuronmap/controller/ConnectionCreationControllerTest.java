package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє взаємодію контролера створення зв’язків із поданням прев’ю та службою зв’язків.
 */
final class ConnectionCreationControllerTest {

    /** Запускає JavaFX toolkit перед перевірками контролера, який працює з графічними вузлами. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє, що контролер делегує прев’ю поданню та прибирає його під час скасування. */
    @Test
    void previewFollowsCursorAndIsRemovedWhenCreationIsCancelled() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            fixture.controller().beginCreate(fixture.source().model().id());

            assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            Line preview = (Line) fixture.workspace().overlayLayer().getChildren().getFirst();

            fixture.controller().handleMouseMoved(mouseEvent(
                    fixture.workspace().node(), MouseEvent.MOUSE_MOVED, MouseButton.NONE,
                    315.0, 270.0
            ));
            assertEquals(315.0, preview.getEndX(), 0.001);
            assertEquals(270.0, preview.getEndY(), 0.001);

            fixture.controller().cancelCreate();

            assertEquals(EditorState.Mode.IDLE, fixture.state().mode());
            assertEquals(Cursor.DEFAULT, fixture.workspace().node().getCursor());
            assertTrue(fixture.workspace().overlayLayer().getChildren().isEmpty());
        });
    }

    /** Перевіряє, що валідна ціль створює зв’язок, зберігає зміни та залишає режим активним. */
    @Test
    void validTargetCreatesConnectionAndKeepsCreationModeActive() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            fixture.controller().beginCreate(fixture.source().model().id());

            fixture.controller().handleCreateClick(mouseEvent(
                    fixture.target(), MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY,
                    12.0, 12.0
            ));

            assertTrue(fixture.connectionService().contains(
                    fixture.source().model().id(), fixture.target().model().id()
            ));
            assertEquals(1, fixture.refreshCalls().get());
            assertEquals(1, fixture.saveCalls().get());
            assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            assertEquals(fixture.source().model().id(), fixture.state().connectionSourceId());
            assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            fixture.controller().cancelCreate();
        });
    }

    /** Перевіряє послідовне створення кількох зв’язків від одного джерела в одному режимі. */
    @Test
    void createsMultipleConnectionsWithoutLeavingCreationMode() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            String sourceId = fixture.source().model().id();
            fixture.controller().beginCreate(sourceId);

            fixture.controller().handleCreateClick(mouseEvent(
                    fixture.target(), MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY,
                    12.0, 12.0
            ));
            fixture.controller().handleCreateClick(mouseEvent(
                    fixture.secondTarget(), MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY,
                    12.0, 12.0
            ));

            assertTrue(fixture.connectionService().contains(sourceId, fixture.target().model().id()));
            assertTrue(fixture.connectionService().contains(sourceId, fixture.secondTarget().model().id()));
            assertEquals(2, fixture.refreshCalls().get());
            assertEquals(2, fixture.saveCalls().get());
            assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            assertEquals(sourceId, fixture.state().connectionSourceId());
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            fixture.controller().cancelCreate();
        });
    }

    /** Перевіряє, що скасування з боку користувача показує повідомлення лише для активного режиму. */
    @Test
    void userCancellationReportsStatusOnceAndDoesNotReportWhenAlreadyIdle() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            fixture.controller().beginCreate(fixture.source().model().id());

            fixture.controller().cancelCreateByUser();

            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            assertEquals(localization.text("status.connection_create_cancelled"), fixture.lastStatus().get());
            assertEquals(EditorState.Mode.IDLE, fixture.state().mode());
            assertTrue(fixture.workspace().overlayLayer().getChildren().isEmpty());
            assertEquals(Cursor.DEFAULT, fixture.workspace().node().getCursor());

            fixture.lastStatus().set(null);
            fixture.controller().cancelCreateByUser();
            assertNull(fixture.lastStatus().get(), "Повторне скасування не має показувати зайве повідомлення");
        });
    }

    /** Перевіряє, що лівий клік по порожньому полотну залишає режим створення активним. */
    @Test
    void clickingBlankCanvasKeepsConnectionCreationActive() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            String sourceId = fixture.source().model().id();
            fixture.controller().beginCreate(sourceId);

            fixture.controller().handleCreateClick(mouseEvent(
                    fixture.workspace().node(), MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY,
                    600.0, 400.0
            ));

            assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            assertEquals(sourceId, fixture.state().connectionSourceId());
            assertEquals(Cursor.CROSSHAIR, fixture.workspace().node().getCursor());
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            assertEquals(0, fixture.refreshCalls().get());
            assertEquals(0, fixture.saveCalls().get());
            fixture.controller().cancelCreate();
        });
    }

    /** Перевіряє, що вибір вихідного нейрона не створює самозв’язок і дозволяє продовжити вибір цілі. */
    @Test
    void selectingSourceAsTargetDoesNotCreateSelfConnection() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            String sourceId = fixture.source().model().id();
            fixture.controller().beginCreate(sourceId);

            fixture.controller().handleCreateClick(mouseEvent(
                    fixture.source(), MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY,
                    12.0, 12.0
            ));

            assertFalse(fixture.connectionService().contains(sourceId, sourceId));
            assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state().mode());
            assertEquals(1, fixture.workspace().overlayLayer().getChildren().size());
            assertEquals(0, fixture.refreshCalls().get());
            assertEquals(0, fixture.saveCalls().get());
            fixture.controller().cancelCreate();
        });
    }

    /**
     * Створює мінімальне полотно з трьома нейронами та реальними службою і станом редактора.
     *
     * @return залежності тестового сценарію зі створеним контролером.
     */
    private static Fixture createFixture() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron sourceModel = model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
        Neuron targetModel = model.createNeuron(NeuronType.INHIBITORY, 340.0, 180.0);
        Neuron secondTargetModel = model.createNeuron(NeuronType.EXCITATORY, 520.0, 300.0);
        NeuronView source = new NeuronView(model.presentation(sourceModel.id()));
        NeuronView target = new NeuronView(model.presentation(targetModel.id()));
        NeuronView secondTarget = new NeuronView(model.presentation(secondTargetModel.id()));
        WorkspaceView workspace = new WorkspaceView();
        workspace.nodeLayer().getChildren().addAll(source, target, secondTarget);
        Scene scene = new Scene(workspace.node(), 800.0, 600.0);
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        EditorState state = new EditorState(1.0, 0.0, 0.0);
        ConnectionService connectionService = new ConnectionService(model);
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicReference<String> lastStatus = new AtomicReference<>();
        ConnectionCreationController controller = new ConnectionCreationController(
                connectionService,
                state,
                workspace,
                new HashMap<>(Map.of(
                        sourceModel.id(), source,
                        targetModel.id(), target,
                        secondTargetModel.id(), secondTarget
                )),
                refreshCalls::incrementAndGet,
                saveCalls::incrementAndGet,
                lastStatus::set,
                () -> { },
                new LocalizationService(Locale.ENGLISH)
        );

        return new Fixture(
                controller,
                state,
                connectionService,
                workspace,
                source,
                target,
                secondTarget,
                refreshCalls,
                saveCalls,
                lastStatus,
                scene
        );
    }

    /**
     * Створює подію миші з указаною ціллю, щоб контролер міг перевірити предка-нейрона.
     *
     * @param target ціль події JavaFX.
     * @param eventType тип події миші.
     * @param button натиснута кнопка миші.
     * @param x координата X у локальній системі координат цілі.
     * @param y координата Y у локальній системі координат цілі.
     * @return подія миші з заданими параметрами.
     */
    private static MouseEvent mouseEvent(
            EventTarget target,
            javafx.event.EventType<MouseEvent> eventType,
            MouseButton button,
            double x,
            double y
    ) {
        return new MouseEvent(
                target,
                target,
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
                button == MouseButton.PRIMARY,
                button == MouseButton.MIDDLE,
                button == MouseButton.SECONDARY,
                false,
                false,
                true,
                null
        );
    }

    /** Виконує тестову дію в потоці JavaFX і передає помилку до потоку JUnit. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Помилка тесту контролера створення зв’язків", failure[0]);
        }
    }

    /** Містить компоненти та лічильники, потрібні для ізольованих сценаріїв створення зв’язків. */
    private record Fixture(
            ConnectionCreationController controller,
            EditorState state,
            ConnectionService connectionService,
            WorkspaceView workspace,
            NeuronView source,
            NeuronView target,
            NeuronView secondTarget,
            AtomicInteger refreshCalls,
            AtomicInteger saveCalls,
            AtomicReference<String> lastStatus,
            Scene scene
    ) {
    }
}
