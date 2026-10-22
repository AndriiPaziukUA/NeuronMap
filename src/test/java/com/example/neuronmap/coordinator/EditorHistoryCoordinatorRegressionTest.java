package com.example.neuronmap.coordinator;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.MainView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє регресії undo/redo для взаємодії, фокуса нейронів і активного сеансу симуляції. */
final class EditorHistoryCoordinatorRegressionTest {

    /** Перевіряє, що undo та redo зберігають режим створення зв’язків і його графічне прев’ю. */
    @Test
    void undoAndRedoKeepConnectionCreationModeActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron target = fixture.neurons.create(NeuronType.INHIBITORY, 240.0, 160.0);
                fixture.history.initialize();
                fixture.install();

                fixture.components.connectionController().beginCreate(source.id());
                assertTrue(fixture.connections.create(source.id(), target.id()));
                fixture.history.commitSavedState();

                fireShortcut(fixture.scene, KeyCode.Z);

                assertFalse(fixture.model.hasConnection(source.id(), target.id()));
                assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state.mode());
                assertEquals(source.id(), fixture.state.connectionSourceId());
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertEquals(1, previewCount(fixture));

                fireShortcut(fixture.scene, KeyCode.Y);

                assertTrue(fixture.model.hasConnection(source.id(), target.id()));
                assertEquals(EditorState.Mode.CREATE_CONNECTION, fixture.state.mode());
                assertEquals(source.id(), fixture.state.connectionSourceId());
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertEquals(1, previewCount(fixture));
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що undo/redo зберігають режим видалення зв’язків і не зупиняють активну симуляцію. */
    @Test
    void undoAndRedoKeepConnectionDeletionModeAndSimulationActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron target = fixture.neurons.create(NeuronType.EXCITATORY, 240.0, 160.0);
                assertTrue(fixture.connections.create(source.id(), target.id()));
                assertTrue(fixture.connections.create(target.id(), source.id()));
                fixture.history.initialize();
                fixture.install();

                fixture.components.connectionController().beginDelete(source.id());
                fixture.components.simulationController().emitPulse(source.id());
                assertTrue(fixture.components.simulationController().hasActiveSimulation());
                assertEquals(EditorState.Mode.DELETE_CONNECTION, fixture.state.mode());

                String outgoingConnectionId = fixture.model.connections().stream()
                        .filter(connection -> connection.sourceId().equals(source.id())
                                && connection.targetId().equals(target.id()))
                        .map(Connection::id)
                        .findFirst()
                        .orElseThrow();
                assertTrue(fixture.connections.remove(outgoingConnectionId));
                fixture.history.commitSavedState();
                fireShortcut(fixture.scene, KeyCode.Z);

                assertTrue(fixture.model.hasConnection(source.id(), target.id()));
                assertEquals(EditorState.Mode.DELETE_CONNECTION, fixture.state.mode());
                assertEquals(source.id(), fixture.state.deleteConnectionNeuronId());
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertTrue(fixture.components.simulationController().hasActiveSimulation());

                fireShortcut(fixture.scene, KeyCode.Y);

                assertFalse(fixture.model.hasConnection(source.id(), target.id()));
                assertEquals(EditorState.Mode.DELETE_CONNECTION, fixture.state.mode());
                assertEquals(source.id(), fixture.state.deleteConnectionNeuronId());
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertTrue(fixture.components.simulationController().hasActiveSimulation());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що undo/redo відновлюють поточні індикатори й анімації сигналу без очікування наступного такту. */
    @Test
    void undoAndRedoRestoreCurrentSignalPresentationImmediately() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron target = fixture.neurons.create(NeuronType.EXCITATORY, 240.0, 160.0);
                Neuron lateTarget = fixture.neurons.create(NeuronType.INHIBITORY, 400.0, 60.0);
                source.setParameters(new NeuronParameters(7, 1));
                assertTrue(fixture.connections.create(source.id(), target.id()));
                fixture.history.initialize();
                fixture.install();

                fixture.components.simulationController().emitPulse(source.id());
                assertTrue(fixture.components.simulationController().hasActiveSimulation());
                assertCurrentSignalPresentation(fixture, source.id(), target.id());
                Node activePulse = fixture.view.workspace().pulseLayer().getChildren().getFirst();
                Connection activeConnection = fixture.model.connections().stream()
                        .filter(connection -> connection.sourceId().equals(source.id())
                                && connection.targetId().equals(target.id()))
                        .findFirst()
                        .orElseThrow();
                ConnectionView activeConnectionView = fixture.connectionViews.get(activeConnection.id());
                assertNotNull(activeConnectionView);

                assertTrue(fixture.connections.create(source.id(), lateTarget.id()));
                fixture.history.commitSavedState();

                fireShortcut(fixture.scene, KeyCode.Z);

                assertFalse(fixture.model.hasConnection(source.id(), lateTarget.id()));
                assertTrue(fixture.components.simulationController().hasActiveSimulation());
                assertCurrentSignalPresentation(fixture, source.id(), target.id());
                assertSame(activePulse, fixture.view.workspace().pulseLayer().getChildren().getFirst(),
                        "Undo не має перезапускати вже активну анімацію сигналу");
                assertSame(activeConnectionView, fixture.connectionViews.get(activeConnection.id()),
                        "Undo має зберегти графічний вузол незміненого зв'язку");
                assertSame(fixture.model.connections().stream()
                                .filter(connection -> connection.id().equals(activeConnection.id()))
                                .findFirst().orElseThrow(), activeConnectionView.model(),
                        "Подання має посилатися на екземпляр зв'язку з відновленої моделі");
                assertEquals(1, fixture.view.workspace().pulseLayer().getChildren().size(),
                        "Undo не має створювати дубльованих анімацій для того самого сигналу");

                fireShortcut(fixture.scene, KeyCode.Y);

                assertTrue(fixture.model.hasConnection(source.id(), lateTarget.id()));
                assertTrue(fixture.components.simulationController().hasActiveSimulation());
                assertCurrentSignalPresentation(fixture, source.id(), target.id());
                assertSame(activePulse, fixture.view.workspace().pulseLayer().getChildren().getFirst(),
                        "Redo не має перезапускати вже активну анімацію сигналу");
                assertSame(activeConnectionView, fixture.connectionViews.get(activeConnection.id()),
                        "Redo має зберегти графічний вузол незміненого зв'язку");
                assertSame(fixture.model.connections().stream()
                                .filter(connection -> connection.id().equals(activeConnection.id()))
                                .findFirst().orElseThrow(), activeConnectionView.model(),
                        "Подання має посилатися на екземпляр зв'язку з повторно відновленої моделі");
                assertEquals(1, fixture.view.workspace().pulseLayer().getChildren().size(),
                        "Redo не має створювати дубльованих анімацій для того самого сигналу");
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє заміну застарілого подання, якщо ідентифікатор зв'язку почав посилатися на інші кінці.
     *
     * @throws Exception якщо сценарій неможливо виконати в JavaFX Application Thread.
     */
    @Test
    void historySynchronizationReplacesConnectionViewWhenEndpointsChange() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron originalTarget = fixture.neurons.create(NeuronType.EXCITATORY, 240.0, 160.0);
                Neuron restoredTarget = fixture.neurons.create(NeuronType.INHIBITORY, 400.0, 60.0);
                assertTrue(fixture.connections.create(source.id(), originalTarget.id()));
                fixture.history.initialize();
                fixture.install();

                Connection originalConnection = fixture.model.connections().iterator().next();
                ConnectionView originalView = fixture.connectionViews.get(originalConnection.id());
                assertNotNull(originalView);

                fixture.model.removeConnection(originalConnection.id());
                fixture.model.addConnection(originalConnection.id(), source.id(), restoredTarget.id());
                fixture.components.presentation().synchronizeViewsAfterHistoryChange();

                ConnectionView restoredView = fixture.connectionViews.get(originalConnection.id());
                assertNotNull(restoredView);
                assertNotSame(originalView, restoredView,
                        "Подання з іншими кінцями не можна повторно використовувати для старого зв'язку");
                assertEquals(restoredTarget.id(), restoredView.model().targetId());
                assertFalse(fixture.view.workspace().edgeLayer().getChildren().contains(originalView));
                assertTrue(fixture.view.workspace().edgeLayer().getChildren().contains(restoredView));
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє фокус і вибір нейрона після скасування його видалення та наступну команду Delete. */
    @Test
    void undoOfNeuronDeletionFocusesAndSelectsRestoredNeuron() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron neuron = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();

                fixture.state.selectOnly(neuron.id());
                assertTrue(fixture.components.neuronController().deleteSelectedNeurons());
                assertNull(fixture.model.neuron(neuron.id()));

                fireShortcut(fixture.scene, KeyCode.Z);

                NeuronView restoredView = fixture.neuronViews.get(neuron.id());
                assertNotNull(restoredView, "Undo має відтворити графічне подання нейрона");
                assertTrue(restoredView.isFocusTraversable(), "Відновлений нейрон має приймати фокус клавіатури");
                assertEquals(1, fixture.state.selectedNeuronIds().size());
                assertTrue(fixture.state.selectedNeuronIds().contains(neuron.id()),
                        "Поточним вибором має стати відновлений нейрон");

                fireUnmodifiedKey(fixture.scene, KeyCode.DELETE);
                assertNull(fixture.model.neuron(neuron.id()),
                        "Delete після undo має видаляти сфокусований і вибраний нейрон");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що redo фокусує останній нейрон, повернутий із кількох нейронів. */
    @Test
    void redoFocusesLastNeuronRestoredByHistory() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                fixture.history.initialize();
                fixture.install();

                fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.neurons.create(NeuronType.INHIBITORY, 240.0, 160.0);
                fixture.components.presentation().refreshAll();
                fixture.history.commitSavedState();

                fireShortcut(fixture.scene, KeyCode.Z);
                assertTrue(fixture.model.neurons().isEmpty());

                fireShortcut(fixture.scene, KeyCode.Y);

                String lastRestoredNeuronId = fixture.neurons.neurons().stream()
                        .map(Neuron::id)
                        .reduce((first, last) -> last)
                        .orElseThrow();
                NeuronView restoredView = fixture.neuronViews.get(lastRestoredNeuronId);
                assertNotNull(restoredView);
                assertEquals(1, fixture.state.selectedNeuronIds().size());
                assertTrue(fixture.state.selectedNeuronIds().contains(lastRestoredNeuronId));
                assertTrue(restoredView.isFocusTraversable(),
                        "Останній відновлений нейрон має приймати клавіатурний фокус");
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Перевіряє анімацію, активний вихід і суму сигналу в поточних поданнях після відновлення карти.
     *
     * @param fixture компоненти редактора та його поточні подання.
     * @param sourceId ідентифікатор активованого нейрона-джерела.
     * @param targetId ідентифікатор нейрона, для якого показано вхідну суму сигналу.
     */
    private static void assertCurrentSignalPresentation(Fixture fixture, String sourceId, String targetId) {
        NeuronView sourceView = fixture.neuronViews.get(sourceId);
        NeuronView targetView = fixture.neuronViews.get(targetId);
        assertEquals(1, fixture.view.workspace().pulseLayer().getChildren().size(),
                "Має залишатися рівно одна анімація чинного сигналу");
        assertTrue(hasStyleClass(sourceView, "port-output-signal-excitatory"),
                "Вихідний порт активного нейрона має залишатися підсвіченим");
        assertTrue(hasLabelText(targetView, "7"),
                "На цільовому нейроні має відображатися поточна сума вхідного сигналу");
        assertTrue(hasStyleClass(targetView, "port-input-sufficient"),
                "Вхідний порт має зберігати індикатор достатньої суми сигналу");
    }

    /**
     * Перевіряє наявність тексту серед числових індикаторів нейрона.
     *
     * @param neuronView подання нейрона, яке потрібно перевірити.
     * @param text очікуваний текст індикатора.
     * @return {@code true}, якщо хоча б один дочірній напис містить текст.
     */
    private static boolean hasLabelText(NeuronView neuronView, String text) {
        return neuronView.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .anyMatch(label -> text.equals(label.getText()));
    }

    /**
     * Перевіряє наявність потрібного CSS-класу в дочірньому графічному вузлі нейрона.
     *
     * @param neuronView подання нейрона, яке потрібно перевірити.
     * @param styleClass назва очікуваного CSS-класу.
     * @return {@code true}, якщо клас присутній у дочірньому вузлі.
     */
    private static boolean hasStyleClass(NeuronView neuronView, String styleClass) {
        return neuronView.getChildren().stream()
                .anyMatch(node -> node.getStyleClass().contains(styleClass));
    }

    /**
     * Надсилає Ctrl+Z або Ctrl+Y до сцени через центральний маршрутизатор гарячих клавіш.
     *
     * @param scene сцена редактора з установленим маршрутизатором.
     * @param code клавіша скасування або повторення.
     */
    private static void fireShortcut(Scene scene, KeyCode code) {
        Event.fireEvent(scene.getRoot(), new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false));
    }

    /** Надсилає звичайну клавішу без модифікаторів до маршрутизатора гарячих клавіш редактора. */
    private static void fireUnmodifiedKey(Scene scene, KeyCode code) {
        Event.fireEvent(scene.getRoot(), new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /**
     * Підраховує попередній перегляд активного інструмента, не враховуючи ручки обертання.
     *
     * @param fixture компоненти редактора й активне полотно.
     * @return кількість графічних вузлів попереднього перегляду.
     */
    private static long previewCount(Fixture fixture) {
        return fixture.view.workspace().overlayLayer().getChildren().stream()
                .filter(Node::isMouseTransparent)
                .count();
    }

    /** Складає реальні компоненти редактора зі сховищем без файлових побічних ефектів. */
    private static final class Fixture {

        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neurons = new NeuronService(model);
        private final ConnectionService connections = new ConnectionService(model);
        private final GroupService groups = new GroupService(model);
        private final HistoryService history = new HistoryService(model);
        private final MapService mapService = new MapService(model, new TestMapRepository(model));
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, ConnectionView> connectionViews = new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final AtomicReference<MapPresentationCoordinator> presentationReference = new AtomicReference<>();
        private final AtomicReference<MapInteractionCoordinator> interactionReference = new AtomicReference<>();
        private final AppConfig config = new AppConfig(
                new AppConfig.Window(ApplicationMetadata.NAME, 1280.0, 720.0, "state.properties"),
                new AppConfig.Camera(1.25),
                new AppConfig.Simulation(650.0, 50.0, 5_000.0)
        );
        private final MainView view = new MainView(
                type -> interactionReference.get().beginAddNeuronMode(type),
                () -> interactionReference.get().groupSelection(),
                () -> interactionReference.get().ungroupSelection(),
                () -> { },
                () -> { },
                ignored -> { },
                config.simulation().defaultTickMillis(),
                localization
        );
        private final MapEditorComponentFactory.Components components = createComponents();
        private final Scene scene = createScene();

        /**
         * Складає компоненти редактора з callback-ами, які посилаються на координатори після їх створення.
         *
         * @return створені залежності редактора для інтеграційних перевірок.
         */
        private MapEditorComponentFactory.Components createComponents() {
            return MapEditorComponentFactory.create(new MapEditorComponentFactory.Inputs(
                    config,
                    localization,
                    mapService,
                    neurons,
                    connections,
                    groups,
                    history,
                    state,
                    view,
                    neuronViews,
                    connectionViews,
                    rotationHandles,
                    config.simulation().defaultTickMillis(),
                    new MapEditorComponentFactory.Actions(
                            history::commitSavedState,
                            view::setStatus,
                            () -> {
                                MapPresentationCoordinator presentation = presentationReference.get();
                                if (presentation != null) {
                                    presentation.refreshAll();
                                }
                            },
                            () -> {
                                MapPresentationCoordinator presentation = presentationReference.get();
                                if (presentation != null) {
                                    presentation.refreshNeurons();
                                }
                            },
                            ignored -> { },
                            () -> { },
                            ignored -> { }
                    )
            ));
        }

        /**
         * Публікує координатори та створює сцену для подальшого встановлення обробників.
         *
         * @return сцена тестового редактора.
         */
        private Scene createScene() {
            interactionReference.set(components.interaction());
            presentationReference.set(components.presentation());
            return view.createScene(800.0, 600.0);
        }

        /** Реєструє контролери й синхронізує початкові графічні подання з картою. */
        private void install() {
            components.neuronController().loadViews();
            components.presentation().refreshAll();
            components.interaction().install();
            components.connectionController().install();
            components.keyboardController().install(scene);
        }

        /** Звільняє контролери, таймери, подання та тестове сховище після кожного сценарію. */
        private void dispose() {
            components.keyboardController().dispose();
            components.interaction().dispose();
            components.connectionController().dispose();
            components.neuronController().dispose();
            components.cameraController().dispose();
            components.simulationController().shutdown();
            view.dispose();
            mapService.close();
        }
    }

    /** Реалізує сховище карти в пам’яті для інтеграційних тестів редактора. */
    private static final class TestMapRepository implements MapRepository {

        private final NeuronMapModel model;

        /**
         * Зберігає модель, з якої тестове сховище копіює початкові дані.
         *
         * @param model модель, що слугує джерелом даних тестового сховища.
         */
        private TestMapRepository(NeuronMapModel model) {
            this.model = model;
        }

        /**
         * Повертає тестовий шлях без створення файла.
         *
         * @return шлях-заглушка, який не створює файл.
         */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /**
         * Повертає стандартний стан камери без звернення до диска.
         *
         * @return типовий стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /**
         * Повертає надане резервне значення тривалості такту.
         *
         * @param fallbackMillis резервна тривалість такту симуляції.
         * @return передане резервне значення.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /**
         * Копіює тестовий стан моделі в цільову модель.
         *
         * @param target модель, у яку копіюється тестовий стан.
         */
        @Override
        public void loadInto(NeuronMapModel target) {
            target.replaceContentsFrom(model);
        }

        /**
         * Не виконує файлових операцій для налаштувань симуляції.
         *
         * @param millis тривалість такту, яку не потрібно зберігати в тестовому сховищі.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /**
         * Приймає збереження без запису на диск.
         *
         * @param ignored модель карти, яка не записується на диск.
         * @param cameraState стан камери, який у цій заглушці не зберігається.
         */
        @Override
        public void save(NeuronMapModel ignored, CameraState cameraState) {
        }

        /** Звільняє сховище, яке не володіє зовнішніми ресурсами. */
        @Override
        public void close() {
        }
    }
}
