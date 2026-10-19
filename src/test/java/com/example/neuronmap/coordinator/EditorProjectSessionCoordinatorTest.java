package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє перемикання проєктів, відновлення їхньої швидкості та відсутність зайвого збереження. */
final class EditorProjectSessionCoordinatorTest {

    /** Перевіряє, що звичайне перемикання не записує незмінений проєкт повторно. */
    @Test
    void doesNotSaveProjectUnconditionallyBeforeSwitch() {
        NeuronMapModel model = new NeuronMapModel();
        MapService mapService = new MapService(model, new NoOpMapRepository());
        HistoryService historyService = new HistoryService(model);
        EditorState state = new EditorState(CameraState.defaultState());
        AtomicInteger saveCalls = new AtomicInteger();

        EditorProjectSessionCoordinator coordinator = new EditorProjectSessionCoordinator(
                mapService,
                historyService,
                state,
                ignored -> { },
                ignored -> { },
                ignored -> { },
                100.0,
                5000.0,
                650.0,
                new RecordingSessionActions(saveCalls, new AtomicReference<>())
        );

        coordinator.prepareForProjectSwitch();

        assertEquals(0, saveCalls.get());
    }

    /** Перевіряє, що активація збереженого проєкту синхронно оновлює runtime та отримувача стану інтерфейсу. */
    @Test
    void restoresProjectSpeedIntoRuntimeOwnerAndUserInterface() {
        NeuronMapModel model = new NeuronMapModel();
        MapService mapService = new MapService(model, new NoOpMapRepository());
        HistoryService historyService = new HistoryService(model);
        EditorState state = new EditorState(CameraState.defaultState());
        AtomicReference<Double> runtimeSpeed = new AtomicReference<>();
        AtomicReference<Double> uiSpeed = new AtomicReference<>();
        EditorProjectSessionCoordinator coordinator = new EditorProjectSessionCoordinator(
                mapService, historyService, state, ignored -> { }, runtimeSpeed::set, ignored -> { },
                100.0, 5000.0, 650.0,
                new RecordingSessionActions(new AtomicInteger(), uiSpeed)
        );
        NoOpMapRepository savedProject = new NoOpMapRepository(425.0);

        coordinator.activateProject(projectDescriptor(), savedProject, true);

        assertEquals(425.0, runtimeSpeed.get().doubleValue());
        assertEquals(runtimeSpeed.get(), uiSpeed.get());
        assertTrue(mapService.isActiveRepository(savedProject));
        assertEquals(425.0, mapService.loadSimulationTickMillis(650.0));
    }

    /** Перевіряє, що відновлення ігнорує збережену швидкість поза межами та застосовує конфігураційний default. */
    @Test
    void fallsBackToConfiguredDefaultForOutOfRangeProjectSpeed() {
        NeuronMapModel model = new NeuronMapModel();
        MapService mapService = new MapService(model, new NoOpMapRepository());
        HistoryService historyService = new HistoryService(model);
        EditorState state = new EditorState(CameraState.defaultState());
        AtomicReference<Double> runtimeSpeed = new AtomicReference<>();
        AtomicReference<Double> uiSpeed = new AtomicReference<>();
        EditorProjectSessionCoordinator coordinator = new EditorProjectSessionCoordinator(
                mapService, historyService, state, ignored -> { }, runtimeSpeed::set, ignored -> { },
                100.0, 5000.0, 650.0,
                new RecordingSessionActions(new AtomicInteger(), uiSpeed)
        );

        coordinator.activateProject(projectDescriptor(), new NoOpMapRepository(5001.0), true);

        assertEquals(650.0, runtimeSpeed.get().doubleValue());
        assertEquals(runtimeSpeed.get(), uiSpeed.get());
    }

    /** Створює стабільний опис постійного проєкту для перевірок активації. */
    private static ProjectDescriptor projectDescriptor() {
        return new ProjectDescriptor(
                "persisted-project", "Project", Path.of("Project/project.db"), Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    /** Реалізує нейтральні дії сесії та фіксує швидкість, передану для відображення. */
    private static final class RecordingSessionActions implements EditorProjectSessionCoordinator.SessionActions {
        private final AtomicInteger saveCalls;
        private final AtomicReference<Double> uiSpeed;

        /** Створює тестові дії з лічильником збережень і спостерігачем швидкості інтерфейсу. */
        private RecordingSessionActions(AtomicInteger saveCalls, AtomicReference<Double> uiSpeed) {
            this.saveCalls = saveCalls;
            this.uiSpeed = uiSpeed;
        }

        /** Зупиняє симуляцію без додаткових дій у цьому тесті. */
        @Override public void stopSimulation() { }
        /** Очищає підсвічування без графічного середовища. */
        @Override public void clearConnectionHighlights() { }
        /** Скасовує взаємодії без графічного середовища. */
        @Override public void cancelInteractions() { }
        /** Приховує меню без графічного середовища. */
        @Override public void hideNeuronMenu() { }
        /** Скасовує відкладене збереження без графічного середовища. */
        @Override public void cancelPendingCameraSave() { }
        /** Фіксує спробу примусового збереження для регресійної перевірки. */
        @Override public void saveCurrentProject() { saveCalls.incrementAndGet(); }
        /** Повертає, що симуляція не була активною. */
        @Override public boolean pauseSimulationForModal() { return false; }
        /** Не відновлює симуляцію, яку цей тест не запускає. */
        @Override public void resumeSimulationFromModal() { }
        /** Призупиняє анімації без графічного середовища. */
        @Override public void pauseConnectionAnimations() { }
        /** Відновлює анімації без графічного середовища. */
        @Override public void resumeConnectionAnimations() { }
        /** Записує швидкість, яку сесія передала до інтерфейсу. */
        @Override public void updateSimulationSpeed(double millis) { uiSpeed.set(millis); }
        /** Не виконує графічної синхронізації в тесті. */
        @Override public void synchronizeViewsWithModel() { }
        /** Не застосовує камеру без графічного середовища. */
        @Override public void applyCamera() { }
        /** Не навігує меню в тесті. */
        @Override public void showMainPage() { }
    }

    /** Тестовий репозиторій, що повертає налаштовану швидкість та не виконує файлових операцій. */
    private static final class NoOpMapRepository implements MapRepository {
        private final Double storedTickMillis;

        /** Створює репозиторій без збереженої швидкості. */
        private NoOpMapRepository() {
            this(null);
        }

        /** Створює репозиторій із заданою тривалістю такту для сценарію повторного відкриття. */
        private NoOpMapRepository(Double storedTickMillis) {
            this.storedTickMillis = storedTickMillis;
        }

        /** Повертає стабільний шлях тестового проєкту. */
        @Override public Path databasePath() { return Path.of("unused-test-project.db"); }
        /** Повертає типовий стан камери, оскільки камера не є предметом цих тестів. */
        @Override public CameraState loadCameraState() { return CameraState.defaultState(); }
        /** Повертає збережену швидкість або конфігураційне резервне значення. */
        @Override public double loadSimulationTickMillis(double fallbackMillis) {
            return storedTickMillis == null ? fallbackMillis : storedTickMillis;
        }
        /** Не змінює модель у тестовому репозиторії. */
        @Override public void loadInto(NeuronMapModel model) { }
        /** Не зберігає налаштування, бо цей репозиторій використано для тестування відновлення. */
        @Override public void saveSimulationTickMillis(double millis) { }
        /** Не зберігає карту в тестовому репозиторії. */
        @Override public void save(NeuronMapModel model, CameraState cameraState) { }
        /** Не має ресурсів для закриття. */
        @Override public void close() { }
    }
}
