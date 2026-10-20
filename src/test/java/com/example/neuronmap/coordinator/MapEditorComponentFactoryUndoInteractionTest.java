package com.example.neuronmap.coordinator;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
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
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що скасування видалення не завершує життєвий цикл обробників полотна й інструментів. */
final class MapEditorComponentFactoryUndoInteractionTest {

    /**
     * Відтворює видалення нейрона та Ctrl+Z через зібраний граф редактора, після чого перевіряє додавання через панель.
     *
     * @throws Exception якщо JavaFX не може виконати перевірку у своєму потоці.
     */
    @Test
    void undoAfterDeletionKeepsCanvasAndToolbarHandlersInstalled() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron original = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();

                fixture.state.selectOnly(original.id());
                assertTrue(fixture.components.neuronController().deleteSelectedNeurons());
                assertTrue(fixture.neurons.find(original.id()).isEmpty());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(true));

                assertTrue(fixture.neurons.find(original.id()).isPresent(),
                        "Ctrl+Z має відновити видалений нейрон");

                fixture.state.selectOnly(original.id());
                Point2D blankPoint = fixture.view.workspace().node().localToScene(240.0, 180.0);
                Event.fireEvent(fixture.view.workspace().node(), mouseClickedEvent(240.0, 180.0, blankPoint));
                assertTrue(fixture.state.selectedNeuronIds().isEmpty(),
                        "Клік по порожній дошці має знімати вибір після Ctrl+Z");

                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Кнопка панелі повинна й надалі вмикати режим додавання");

                Point2D scenePoint = fixture.view.workspace().node().localToScene(280.0, 210.0);
                Event.fireEvent(fixture.view.workspace().node(), mouseClickedEvent(280.0, 210.0, scenePoint));

                assertEquals(2, fixture.neurons.neurons().size(),
                        "Клік по полотну після Ctrl+Z повинен створити новий нейрон");
                assertTrue(fixture.state.isIdle(), "Після створення нейрона режим додавання має завершитися");
            } finally {
                fixture.dispose();
            }
        });
    }

    /**
     * Створює подію Ctrl+Z для маршрутизатора гарячих клавіш редактора.
     *
     * @param controlDown ознака натиснутої клавіші Ctrl.
     * @return сформована подія натискання клавіші Z.
     */
    private static javafx.scene.input.KeyEvent keyPressedEvent(boolean controlDown) {
        return new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                "",
                "",
                javafx.scene.input.KeyCode.Z,
                false,
                controlDown,
                false,
                false
        );
    }

    /**
     * Створює первинне клацання на полотні з координатами локального вузла та відповідною точкою сцени.
     *
     * @param x локальна координата X робочої області.
     * @param y локальна координата Y робочої області.
     * @param scenePoint та сама точка в системі координат сцени.
     * @return подія первинного клацання.
     */
    private static MouseEvent mouseClickedEvent(double x, double y, Point2D scenePoint) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                x,
                y,
                scenePoint.getX(),
                scenePoint.getY(),
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null
        );
    }

    /** Складає реальні компоненти редактора з тестовим сховищем для перевірки маршрутизації подій після undo. */
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

        /** Складає компоненти після налаштування callback-ів до координаторів, які з'являться під час створення. */
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

        /** Публікує координатори та створює сцену до реєстрації обробників подій. */
        private Scene createScene() {
            interactionReference.set(components.interaction());
            presentationReference.set(components.presentation());
            return view.createScene(800.0, 600.0);
        }

        /** Реєструє маршрутизатори подій і створює початкові графічні подання карти. */
        private void install() {
            components.neuronController().loadViews();
            components.presentation().refreshAll();
            components.interaction().install();
            components.connectionController().install();
            components.keyboardController().install(scene);
        }

        /** Звільняє всі створені компоненти, щоб не залишати обробників і ресурсів JavaFX після тесту. */
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

    /** Реалізує сховище без файлових побічних ефектів, потрібне лише для складання редакторських компонентів. */
    private static final class TestMapRepository implements MapRepository {

        private final NeuronMapModel model;

        /**
         * Зберігає початкову модель, яку тестове сховище повертає під час завантаження.
         *
         * @param model модель, що використовується тестовим сховищем.
         */
        private TestMapRepository(NeuronMapModel model) {
            this.model = model;
        }

        /** Повертає стабільний тестовий шлях до бази даних. */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /** Повертає типовий стан камери, бо файл налаштувань у тесті не створюється. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає резервну швидкість симуляції без читання зовнішнього файла. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /** Копіює поточну тестову модель у модель, передану для завантаження. */
        @Override
        public void loadInto(NeuronMapModel target) {
            target.replaceContentsFrom(model);
        }

        /** Не зберігає швидкість, оскільки перевірка не стосується файлової персистентності. */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /** Приймає виклик збереження без доступу до диска. */
        @Override
        public void save(NeuronMapModel ignored, CameraState cameraState) {
        }

        /** Звільняє тестове сховище без зовнішніх ресурсів. */
        @Override
        public void close() {
        }
    }
}
