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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє взаємодію полотна й інструментів після undo та тривале розміщення нейронів. */
final class MapEditorComponentFactoryUndoInteractionTest {

    /** Перевіряє, що undo не від'єднує обробники, а режим додавання лишається активним до явного скасування. */
    @Test
    void undoKeepsHandlersInstalledAndToolbarPlacementModePersistent() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron original = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();

                fixture.state.selectOnly(original.id());
                assertTrue(fixture.components.neuronController().deleteSelectedNeurons());
                assertTrue(fixture.neurons.find(original.id()).isEmpty());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.Z, true));

                assertTrue(fixture.neurons.find(original.id()).isPresent(),
                        "Ctrl+Z має відновити видалений нейрон");

                fixture.state.selectOnly(original.id());
                Point2D blankPoint = fixture.view.workspace().node().localToScene(240.0, 180.0);
                Event.fireEvent(fixture.view.workspace().node(), mouseClickedEvent(240.0, 180.0, blankPoint));
                assertTrue(fixture.state.selectedNeuronIds().isEmpty(),
                        "Клік по порожній дошці має знімати вибір після Ctrl+Z");

                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Кнопка панелі має вмикати режим додавання");
                assertTrue(fixture.view.toolbar().addExcitatoryButton().getStyleClass()
                                .contains("tool-button-active"),
                        "Активна кнопка має залишатися виділеною");

                clickWorkspace(fixture, 280.0, 210.0);
                assertEquals(2, fixture.neurons.neurons().size(),
                        "Перший клік по полотну має створити нейрон");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Після створення першого нейрона режим має залишатися активним");
                assertEquals(1, previewCount(fixture),
                        "Попередній перегляд не має зникати після створення нейрона");

                clickWorkspace(fixture, 340.0, 250.0);
                assertEquals(3, fixture.neurons.neurons().size(),
                        "Наступний клік має створити ще один нейрон без повторної активації інструмента");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Режим має залишатися активним після кожного розміщення");
                assertEquals(1, previewCount(fixture),
                        "Попередній перегляд має залишатися видимим і після повторного розміщення");

                Event.fireEvent(fixture.view.toolbar().addInhibitoryButton(), new ActionEvent());
                assertEquals(NeuronType.INHIBITORY, fixture.state.pendingNeuronType(),
                        "Натискання іншої кнопки має перемикати тип активного інструмента");
                assertFalse(fixture.view.toolbar().addExcitatoryButton().getStyleClass()
                                .contains("tool-button-active"),
                        "Попередня кнопка має втратити виділення");
                assertTrue(fixture.view.toolbar().addInhibitoryButton().getStyleClass()
                                .contains("tool-button-active"),
                        "Виділеною має бути лише нова кнопка");

                clickWorkspace(fixture, 400.0, 290.0);
                assertEquals(4, fixture.neurons.neurons().size(),
                        "Перемикання типу не має вимикати режим повторного розміщення");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.ESCAPE, false));
                assertTrue(fixture.state.isIdle(), "Esc має вимикати режим розміщення");
                assertFalse(fixture.view.toolbar().addExcitatoryButton().getStyleClass()
                                .contains("tool-button-active"));
                assertFalse(fixture.view.toolbar().addInhibitoryButton().getStyleClass()
                                .contains("tool-button-active"));
                assertEquals(0, previewCount(fixture),
                        "Вимкнення режиму через Esc має прибирати попередній перегляд");

                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());
                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());
                assertTrue(fixture.state.isIdle(), "Повторне натискання тієї самої кнопки має вимикати режим");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що видалення поточного вибору не вимикає активний інструмент розміщення нейронів. */
    @Test
    void deletingSelectionKeepsNeuronPlacementModeActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron original = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();
                fixture.state.selectOnly(original.id());

                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());
                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.DELETE, false));

                assertTrue(fixture.neurons.find(original.id()).isEmpty(),
                        "Команда Delete має виконати видалення вибраного нейрона");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Видалення вибору не має вимикати режим розміщення");
                assertEquals(NeuronType.EXCITATORY, fixture.state.pendingNeuronType());
                assertTrue(fixture.view.toolbar().addExcitatoryButton().getStyleClass()
                                .contains("tool-button-active"),
                        "Кнопка активного інструмента має залишатися виділеною після видалення");
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor(),
                        "Курсор має зберігати вигляд активного режиму розміщення");

                clickWorkspace(fixture, 280.0, 210.0);
                assertEquals(1, fixture.neurons.neurons().size(),
                        "Після видалення має залишатися можливість додавати нейрони без повторної активації");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode());
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що undo/redo змінює карту, але не вимикає активний інструмент розміщення нейронів. */
    @Test
    void undoAndRedoKeepNeuronPlacementModeActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();
                Event.fireEvent(fixture.view.toolbar().addExcitatoryButton(), new ActionEvent());

                clickWorkspace(fixture, 280.0, 210.0);
                assertEquals(2, fixture.neurons.neurons().size());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.Z, true));
                assertEquals(1, fixture.neurons.neurons().size(), "Undo має скасувати додавання нейрона");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Undo не має вимикати активний інструмент");
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertEquals(1, previewCount(fixture), "Попередній перегляд має залишатися видимим після undo");

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.Y, true));
                assertEquals(2, fixture.neurons.neurons().size(), "Redo має повторити додавання нейрона");
                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode(),
                        "Redo не має вимикати активний інструмент");
                assertTrue(fixture.view.toolbar().addExcitatoryButton().getStyleClass()
                        .contains("tool-button-active"));
                assertEquals(Cursor.CROSSHAIR, fixture.view.workspace().node().getCursor());
                assertEquals(1, previewCount(fixture), "Попередній перегляд має залишатися видимим після redo");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Створює клавіатурну подію для перевірки гарячих клавіш зібраного редактора. */
    private static KeyEvent keyPressedEvent(KeyCode code, boolean controlDown) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                code,
                false,
                controlDown,
                false,
                false
        );
    }

    /** Клацає по полотну в точці, вказаній у його локальній системі координат. */
    private static void clickWorkspace(Fixture fixture, double x, double y) {
        Point2D scenePoint = fixture.view.workspace().node().localToScene(x, y);
        Event.fireEvent(fixture.view.workspace().node(), mouseMovedEvent(x, y, scenePoint));
        Event.fireEvent(fixture.view.workspace().node(), mouseClickedEvent(x, y, scenePoint));
    }

    /** Підраховує лише попередні перегляди інструмента, не враховуючи ручки обертання на тому самому шарі. */
    private static long previewCount(Fixture fixture) {
        return fixture.view.workspace().overlayLayer().getChildren().stream()
                .filter(Node::isMouseTransparent)
                .count();
    }

    /** Створює подію руху миші для перевірки попереднього перегляду, прив'язаного до курсора. */
    private static MouseEvent mouseMovedEvent(double x, double y, Point2D scenePoint) {
        return new MouseEvent(
                MouseEvent.MOUSE_MOVED,
                x,
                y,
                scenePoint.getX(),
                scenePoint.getY(),
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                null
        );
    }

    /** Створює первинне клацання на полотні з локальними й відповідними координатами сцени. */
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

    /** Складає реальні компоненти редактора з тестовим сховищем для перевірки маршрутизації подій. */
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

        /** Складає компоненти після налаштування callback-ів до координаторів, що створюються. */
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

        /** Реєструє обробники й створює початкові графічні подання карти. */
        private void install() {
            components.neuronController().loadViews();
            components.presentation().refreshAll();
            components.interaction().install();
            components.connectionController().install();
            components.keyboardController().install(scene);
        }

        /** Звільняє створені компоненти, щоб не залишати обробників і ресурсів JavaFX після тесту. */
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

    /** Реалізує сховище без файлових побічних ефектів, потрібне для складання компонентів редактора. */
    private static final class TestMapRepository implements MapRepository {

        private final NeuronMapModel model;

        /** Зберігає початкову модель, яку тестове сховище повертає під час завантаження. */
        private TestMapRepository(NeuronMapModel model) {
            this.model = model;
        }

        /** Повертає стабільний тестовий шлях до бази даних. */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /** Повертає типовий стан камери без читання зовнішніх файлів. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає передану резервну швидкість симуляції без файлових операцій. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /** Копіює тестову модель у модель, яку передано на завантаження. */
        @Override
        public void loadInto(NeuronMapModel target) {
            target.replaceContentsFrom(model);
        }

        /** Не зберігає швидкість, бо тест не перевіряє файлову персистентність. */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /** Приймає виклик збереження без доступу до диска. */
        @Override
        public void save(NeuronMapModel ignored, CameraState cameraState) {
        }

        /** Звільняє тестове сховище, яке не використовує зовнішніх ресурсів. */
        @Override
        public void close() {
        }
    }
}
