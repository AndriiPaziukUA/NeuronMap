package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
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
 * Перевіряє бізнес-дію зміни напрямку в контролері меню та узгодження залежних подань і збереження.
 */
final class NeuronMenuControllerDirectionTest {

    /**
     * Запускає JavaFX toolkit перед створенням контролерів та графічних вузлів.
     *
     * @throws Exception якщо toolkit не запускається у відведений час.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    /**
     * Перевіряє, що успішна зміна напрямку оновлює всі залежні подання, зберігає стан і показує статус.
     *
     * @throws Exception якщо виконання перевірки в потоці JavaFX не завершується вчасно.
     */
    @Test
    void togglingDirectionRefreshesViewsAndSavesChanges() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();
            NeuronPresentation presentation = fixture.neuronService().presentation(
                    fixture.neuron().id()
            );

            assertFalse(presentation.directionReversed());
            fixture.controller().toggleDirection(fixture.neuron().id());

            assertTrue(presentation.directionReversed());
            assertEquals(1, fixture.visualRefreshes().get());
            assertEquals(1, fixture.connectionRefreshes().get());
            assertEquals(1, fixture.overlayRefreshes().get());
            assertEquals(1, fixture.saves().get());
            assertEquals(
                    fixture.localization().text("status.direction_changed"),
                    fixture.status().get()
            );
            assertTrue(fixture.historyService().canUndo());
            assertTrue(fixture.historyService().undo());
            assertFalse(
                    fixture.neuronService()
                            .presentation(fixture.neuron().id())
                            .directionReversed()
            );
            assertTrue(fixture.historyService().redo());
            assertTrue(
                    fixture.neuronService()
                            .presentation(fixture.neuron().id())
                            .directionReversed()
            );
        });
    }

    /**
     * Перевіряє, що за відсутності нейрона команда не виконує оновлень, не зберігає дані та не змінює статус.
     *
     * @throws Exception якщо виконання перевірки в потоці JavaFX не завершується вчасно.
     */
    @Test
    void missingNeuronDoesNotTriggerRefreshSaveOrStatus() throws Exception {
        runOnFxThread(() -> {
            Fixture fixture = createFixture();

            fixture.controller().toggleDirection("missing-neuron");

            assertEquals(0, fixture.visualRefreshes().get());
            assertEquals(0, fixture.connectionRefreshes().get());
            assertEquals(0, fixture.overlayRefreshes().get());
            assertEquals(0, fixture.saves().get());
            assertNull(fixture.status().get());
        });
    }

    /**
     * Створює контролер із реальними службами та лічильниками зовнішніх дій для ізольованої перевірки.
     *
     * @return об'єкти тестового сценарію та спостережувані результати викликів.
     */
    private static Fixture createFixture() {
        LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        NeuronMapModel model = new NeuronMapModel();
        NeuronService neuronService = new NeuronService(model);
        Neuron neuron = neuronService.create(NeuronType.EXCITATORY, 100.0, 80.0);
        HistoryService historyService = new HistoryService(model);
        historyService.initialize();
        NeuronView neuronView = new NeuronView(neuronService.presentation(neuron.id()));
        Map<String, NeuronView> neuronViews = new HashMap<>();
        neuronViews.put(neuron.id(), neuronView);

        EditorState state = new EditorState(1.0, 0.0, 0.0);
        WorkspaceView workspace = new WorkspaceView();
        AtomicInteger visualRefreshes = new AtomicInteger();
        AtomicInteger connectionRefreshes = new AtomicInteger();
        AtomicInteger overlayRefreshes = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<String> status = new AtomicReference<>();

        ConnectionController connections = new ConnectionController(
                new ConnectionService(model),
                state,
                workspace,
                neuronViews,
                () -> { },
                () -> { },
                message -> { },
                localization
        );
        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace,
                neuronViews,
                () -> { },
                new PulseAnimationController(workspace)
        );
        SimulationController simulation = new SimulationController(
                neuronService,
                new SimulationService(),
                stepPresenter,
                () -> { },
                message -> { },
                paused -> { },
                active -> { },
                100.0,
                10.0,
                1000.0,
                localization
        );
        NeuronDeletionController deletion = new NeuronDeletionController(
                neuronService,
                state,
                workspace,
                () -> { },
                () -> { },
                () -> { },
                message -> { },
                localization
        );
        NeuronMenuView menuView = new NeuronMenuView(
                new VBox(),
                state::selectedNeuronForMenu,
                null,
                localization
        );
        NeuronMenuController controller = new NeuronMenuController(
                neuronService,
                state,
                workspace,
                connections,
                simulation,
                deletion,
                menuView,
                visualRefreshes::incrementAndGet,
                connectionRefreshes::incrementAndGet,
                overlayRefreshes::incrementAndGet,
                () -> {
                    saves.incrementAndGet();
                    historyService.commitSavedState();
                },
                status::set,
                localization
        );

        return new Fixture(
                controller,
                neuronService,
                historyService,
                neuron,
                visualRefreshes,
                connectionRefreshes,
                overlayRefreshes,
                saves,
                status,
                localization
        );
    }

    /**
     * Виконує дію у потоці JavaFX і повертає винятки до тестового потоку.
     *
     * @param action перевірка, яку потрібно виконати.
     * @throws Exception якщо дія не завершується у відведений час.
     */
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX test timed out");
        }
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /**
     * Містить створені залежності та лічильники, що дозволяють перевірити побічні ефекти зміни напрямку.
     *
     * @param controller контролер меню, для якого виконується команда.
     * @param neuronService служба й модель, через яку можна перевірити змінений напрямок.
     * @param historyService історія, що перевіряє скасування й повторення зміни напрямку.
     * @param neuron нейрон, над яким виконується дія.
     * @param visualRefreshes кількість оновлень подань нейронів.
     * @param connectionRefreshes кількість оновлень зв'язків.
     * @param overlayRefreshes кількість оновлень накладок.
     * @param saves кількість запитів на збереження.
     * @param status останнє передане повідомлення статусу.
     * @param localization служба локалізації очікуваного повідомлення.
     */
    private record Fixture(
            NeuronMenuController controller,
            NeuronService neuronService,
            HistoryService historyService,
            Neuron neuron,
            AtomicInteger visualRefreshes,
            AtomicInteger connectionRefreshes,
            AtomicInteger overlayRefreshes,
            AtomicInteger saves,
            AtomicReference<String> status,
            LocalizationService localization
    ) { }
}
