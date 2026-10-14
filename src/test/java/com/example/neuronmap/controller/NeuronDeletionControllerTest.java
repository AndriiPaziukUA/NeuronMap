package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.WorkspaceView;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє поведінку команди видалення без реєстрації дубльованих клавіатурних подій. */
final class NeuronDeletionControllerTest {

    /** Запускає JavaFX toolkit перед створенням робочої області. */
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

    /** Перевіряє видалення вибраних нейронів із єдиним оновленням подань та збереженням. */
    @Test
    void deletesSelectionAndRefreshesOnlyOnce() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            Neuron first = fixture.neurons().create(NeuronType.EXCITATORY, 10.0, 20.0);
            Neuron second = fixture.neurons().create(NeuronType.INHIBITORY, 30.0, 40.0);
            fixture.state().selectOnly(first.id());
            fixture.state().toggleSelection(second.id());

            assertTrue(fixture.controller().deleteSelectedNeurons());

            assertNull(fixture.neurons().find(first.id()));
            assertNull(fixture.neurons().find(second.id()));
            assertTrue(fixture.state().selectedNeuronIds().isEmpty());
            assertEquals(1, fixture.hideMenuCalls().get());
            assertEquals(1, fixture.refreshCalls().get());
            assertEquals(1, fixture.saveCalls().get());
            assertEquals(1, fixture.statusMessages().size());
        });
    }

    /** Перевіряє, що за відсутності вибору видаляється нейрон, для якого відкрите контекстне меню. */
    @Test
    void deletesNeuronReferencedByContextMenuWhenSelectionIsEmpty() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            Neuron neuron = fixture.neurons().create(NeuronType.EXCITATORY, 10.0, 20.0);
            fixture.state().setSelectedNeuronForMenu(neuron.id());

            assertTrue(fixture.controller().deleteSelectedNeurons());

            assertNull(fixture.neurons().find(neuron.id()));
            assertEquals(1, fixture.refreshCalls().get());
            assertEquals(1, fixture.saveCalls().get());
        });
    }

    /** Перевіряє, що команда без вибраних або наявних нейронів нічого не видаляє й не зберігає. */
    @Test
    void doesNothingWhenNothingIsSelected() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            Neuron neuron = fixture.neurons().create(NeuronType.EXCITATORY, 10.0, 20.0);

            assertFalse(fixture.controller().deleteSelectedNeurons());

            assertNotNull(fixture.neurons().find(neuron.id()));
            assertEquals(0, fixture.hideMenuCalls().get());
            assertEquals(0, fixture.refreshCalls().get());
            assertEquals(0, fixture.saveCalls().get());
            assertTrue(fixture.statusMessages().isEmpty());
        });
    }

    /** Перевіряє наскрізне видалення вибраного нейрона через єдиний маршрутизатор гарячих клавіш. */
    @Test
    void deletesSelectedNeuronThroughCentralKeyboardRouter() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            Neuron neuron = fixture.neurons().create(NeuronType.EXCITATORY, 10.0, 20.0);
            fixture.state().selectOnly(neuron.id());
            Scene scene = new Scene(fixture.workspace().node());
            EditorKeyboardController keyboardController = new EditorKeyboardController(
                    () -> false,
                    () -> false,
                    fixture.controller()::deleteSelectedNeurons,
                    () -> { }
            );
            keyboardController.install(scene);

            Event.fireEvent(
                    fixture.workspace().node(),
                    new KeyEvent(
                            KeyEvent.KEY_PRESSED,
                            "",
                            "",
                            KeyCode.DELETE,
                            false,
                            false,
                            false,
                            false
                    )
            );

            assertNull(fixture.neurons().find(neuron.id()));
            assertEquals(1, fixture.refreshCalls().get());
            assertEquals(1, fixture.saveCalls().get());
            keyboardController.dispose();
        });
    }

    /** Перевіряє, що контролер видалення не реєструє власного обробника клавіатури на сцені. */
    @Test
    void doesNotRegisterAnIndependentDeleteKeyHandler() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            Neuron neuron = fixture.neurons().create(NeuronType.EXCITATORY, 10.0, 20.0);
            fixture.state().selectOnly(neuron.id());
            Scene scene = new Scene(fixture.workspace().node());

            Event.fireEvent(
                    fixture.workspace().node(),
                    new KeyEvent(
                            KeyEvent.KEY_PRESSED,
                            "",
                            "",
                            KeyCode.DELETE,
                            false,
                            false,
                            false,
                            false
                    )
            );

            assertNotNull(fixture.neurons().find(neuron.id()));
            assertEquals(0, fixture.refreshCalls().get());
            assertEquals(0, fixture.saveCalls().get());
            assertEquals(fixture.workspace().node(), scene.getRoot());
        });
    }

    /** Створює контролер зі справжньою моделлю та лічильниками зовнішніх дій. */
    private static Fixture createFixture() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neuronService = new NeuronService(model);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        WorkspaceView workspace = new WorkspaceView();
        AtomicInteger hideMenuCalls = new AtomicInteger();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicInteger saveCalls = new AtomicInteger();
        List<String> statusMessages = new ArrayList<>();
        NeuronDeletionController controller = new NeuronDeletionController(
                neuronService,
                state,
                workspace,
                hideMenuCalls::incrementAndGet,
                refreshCalls::incrementAndGet,
                saveCalls::incrementAndGet,
                statusMessages::add,
                new LocalizationService(Locale.ENGLISH)
        );
        return new Fixture(
                controller,
                neuronService,
                state,
                workspace,
                hideMenuCalls,
                refreshCalls,
                saveCalls,
                statusMessages
        );
    }

    /** Виконує дію в потоці JavaFX та передає помилку виконання до JUnit-потоку. */
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
            throw new AssertionError("Помилка тесту видалення нейронів", failure[0]);
        }
    }

    /** Містить стан і лічильники для ізольованої перевірки команди видалення. */
    private record Fixture(
            NeuronDeletionController controller,
            NeuronService neurons,
            EditorState state,
            WorkspaceView workspace,
            AtomicInteger hideMenuCalls,
            AtomicInteger refreshCalls,
            AtomicInteger saveCalls,
            List<String> statusMessages
    ) {
    }
}
