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
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє безперервність симуляції під час роботи з тулбаром і скасування режимів редактора. */
final class MapInteractionCoordinatorConnectionCancellationTest {

    /** Перевіряє повний клавіатурний маршрут і захищає повідомлення створення зв’язку від перезапису статусом ready. */
    @Test
    void escapeCancellationKeepsConnectionCancellationStatusVisible() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();
                fixture.components.connectionController().beginCreate(source.id());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.ESCAPE));

                assertEquals(EditorState.Mode.IDLE, fixture.state.mode());
                assertEquals(Cursor.DEFAULT, fixture.view.workspace().node().getCursor());
                assertEquals(fixture.localization.text("status.connection_create_cancelled"),
                        fixture.lastStatus.get(),
                        "Загальне повідомлення ready не має перезаписувати повідомлення про скасування зв’язку");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що інструменти тулбару й фактичне розміщення нейрона не зупиняють сигнали. */
    @Test
    void nonSimulationToolbarActionsAndNeuronPlacementKeepActiveSimulationRunning() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                fixture.history.initialize();
                fixture.install();
                fixture.components.simulationController().emitPulse(source.id());
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Тест має починатися з активного сигналу");

                fixture.view.toolbar().addExcitatoryButton().fire();

                assertEquals(EditorState.Mode.ADD_NEURON, fixture.state.mode());
                assertEquals(NeuronType.EXCITATORY, fixture.state.pendingNeuronType());
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Вибір інструмента додавання не повинен завершувати активний сигнал");

                fixture.view.toolbar().addInhibitoryButton().fire();

                assertEquals(NeuronType.INHIBITORY, fixture.state.pendingNeuronType());
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Перемикання між інструментами також не повинно завершувати активний сигнал");

                toolbarButton(fixture, "toolbar.group").fire();
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Натискання групування не повинно завершувати активний сигнал");

                toolbarButton(fixture, "toolbar.ungroup").fire();
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Натискання розгрупування не повинно завершувати активний сигнал");

                int initialNeuronCount = fixture.model.neurons().size();
                clickWorkspace(fixture, 240.0, 180.0);

                assertEquals(initialNeuronCount + 1, fixture.model.neurons().size(),
                        "Клацання по дошці має створити нейрон вибраного типу");
                assertTrue(fixture.components.simulationController().hasActiveSimulation(),
                        "Фактичне розміщення нейрона також не повинно завершувати активний сигнал");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що Esc під час додавання нейрона показує повідомлення скасування замість загального статусу готовності. */
    @Test
    void escapeCancellationKeepsNeuronAdditionCancellationStatusVisible() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                fixture.install();
                fixture.components.interaction().beginAddNeuronMode(NeuronType.EXCITATORY);

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.ESCAPE));

                assertEquals(EditorState.Mode.IDLE, fixture.state.mode());
                assertEquals(Cursor.DEFAULT, fixture.view.workspace().node().getCursor());
                assertEquals(fixture.localization.text("status.add_neuron_cancelled"),
                        fixture.lastStatus.get(),
                        "Загальне повідомлення ready не має перезаписувати скасування додавання нейрона");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що Esc скасовує видалення зв’язків і залишає відповідне повідомлення у статус-барі. */
    @Test
    void escapeCancellationKeepsConnectionDeletionCancellationStatusVisible() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron target = fixture.neurons.create(NeuronType.INHIBITORY, 180.0, 120.0);
                assertTrue(fixture.connections.create(source.id(), target.id()));
                fixture.history.initialize();
                fixture.install();
                fixture.components.connectionController().beginDelete(source.id());

                Event.fireEvent(fixture.scene.getRoot(), keyPressedEvent(KeyCode.ESCAPE));

                assertEquals(EditorState.Mode.IDLE, fixture.state.mode());
                assertEquals(Cursor.DEFAULT, fixture.view.workspace().node().getCursor());
                assertEquals(fixture.localization.text("status.connection_delete_cancelled"),
                        fixture.lastStatus.get(),
                        "Загальне повідомлення ready не має перезаписувати повідомлення про скасування видалення зв’язків");
                assertFalse(fixture.connectionViews.values().stream().anyMatch(ConnectionView::isDeleteHighlighted));
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє, що ручне скасування видалення показує повідомлення лише для активного режиму. */
    @Test
    void userCancellationReportsConnectionDeletionStatusOnlyWhenActive() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron source = fixture.neurons.create(NeuronType.EXCITATORY, 80.0, 60.0);
                Neuron target = fixture.neurons.create(NeuronType.INHIBITORY, 180.0, 120.0);
                assertTrue(fixture.connections.create(source.id(), target.id()));
                fixture.history.initialize();
                fixture.install();
                fixture.components.connectionController().beginDelete(source.id());

                fixture.components.connectionController().cancelDeleteByUser();

                String cancellationMessage = fixture.localization.text("status.connection_delete_cancelled");
                assertEquals(cancellationMessage, fixture.lastStatus.get());
                fixture.components.connectionController().cancelDeleteByUser();
                assertEquals(cancellationMessage, fixture.lastStatus.get(),
                        "Повторне скасування не має змінювати повідомлення статус-бару");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Перевіряє локалізований текст скасування видалення зв’язків для всіх підтримуваних мов. */
    @Test
    void connectionDeletionCancellationMessageIsLocalized() {
        assertEquals("Connection deletion cancelled.",
                new LocalizationService(Locale.ENGLISH).text("status.connection_delete_cancelled"));
        assertEquals("Видалення зв'язків скасовано.",
                new LocalizationService(Locale.forLanguageTag("uk")).text("status.connection_delete_cancelled"));
        assertEquals("Удаление связей отменено.",
                new LocalizationService(Locale.forLanguageTag("ru")).text("status.connection_delete_cancelled"));
    }

    /** Імітує користувацьке клацання по полотну через штатний маршрут обробки події.
     *
     * @param fixture тестове оточення редактора.
     * @param x горизонтальна координата на полотні.
     * @param y вертикальна координата на полотні.
     */
    private static void clickWorkspace(Fixture fixture, double x, double y) {
        Point2D scenePoint = fixture.view.workspace().node().localToScene(x, y);
        Event.fireEvent(fixture.view.workspace().node(), new MouseEvent(
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
        ));
    }

    /** Знаходить кнопку тулбару за локалізованим ключем, щоб перевіряти реальні callback-и інструментів. */
    private static Button toolbarButton(Fixture fixture, String localizationKey) {
        String expectedText = fixture.localization.text(localizationKey);
        return fixture.view.toolbar().node().getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> expectedText.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Не знайдено кнопку тулбару: " + localizationKey));
    }

    /** Створює клавіатурну подію Esc для проходження реального маршруту обробки сцени JavaFX. */
    private static KeyEvent keyPressedEvent(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    /** Складає залежності редактора з тестовим сховищем і зберігає останнє повідомлення статусу. */
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
        private final AtomicReference<String> lastStatus = new AtomicReference<>();
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

        /** Складає редактор із реальними контролерами та тестовими callback-ами без файлових побічних ефектів. */
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
                            message -> {
                                lastStatus.set(message);
                                view.setStatus(message);
                            },
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

        /** Публікує координатори та створює сцену до реєстрації клавіатурних обробників. */
        private Scene createScene() {
            interactionReference.set(components.interaction());
            presentationReference.set(components.presentation());
            return view.createScene(800.0, 600.0);
        }

        /** Реєструє контролери та створює подання на основі поточного стану карти. */
        private void install() {
            components.neuronController().loadViews();
            components.presentation().refreshAll();
            components.interaction().install();
            components.connectionController().install();
            components.keyboardController().install(scene);
        }

        /** Звільняє обробники та ресурси, створені тестовим редактором. */
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

    /** Реалізує репозиторій карти без доступу до файлової системи. */
    private static final class TestMapRepository implements MapRepository {

        private final NeuronMapModel model;

        /** Зберігає модель, яку репозиторій використовує для ізольованого завантаження. */
        private TestMapRepository(NeuronMapModel model) {
            this.model = model;
        }

        /** Повертає стабільний умовний шлях, потрібний контракту репозиторію. */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /** Повертає типовий стан камери без читання диска. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає передане значення за замовчуванням для швидкості симуляції. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /** Копіює тестову модель до цільового агрегату карти. */
        @Override
        public void loadInto(NeuronMapModel target) {
            target.replaceContentsFrom(model);
        }

        /** Приймає збереження швидкості без зовнішніх побічних ефектів. */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /** Приймає знімок карти без файлових операцій. */
        @Override
        public void save(NeuronMapModel ignored, CameraState cameraState) {
        }

        /** Звільняє тестовий репозиторій, який не утримує зовнішніх ресурсів. */
        @Override
        public void close() {
        }
    }
}
