package com.example.neuronmap.service;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє безпечне завантаження кандидатного репозиторію та незмінність активної карти в разі помилки.
 */
class MapServiceProjectActivationTest {

    /**
     * Перевіряє, що помилка завантаження після часткової зміни тимчасової моделі не змінює активну карту.
     */
    @Test
    void failedCandidateLoadLeavesActiveModelAndRepositoryUnchanged() {
        NeuronMapModel model = new NeuronMapModel();
        model.addNeuron("active-neuron", NeuronType.EXCITATORY, 1);
        var activeNeuron = model.neuron("active-neuron");
        TestMapRepository activeRepository = new TestMapRepository();
        MapService mapService = new MapService(model, activeRepository);
        TestMapRepository candidate = new TestMapRepository();
        IllegalStateException failure = new IllegalStateException("Збій читання карти");
        candidate.loadFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> mapService.activateRepository(candidate, true, 50.0)
        );

        assertSame(failure, thrown);
        assertSame(activeNeuron, model.neuron("active-neuron"));
        assertEquals(1, model.neurons().size());
        assertNotNull(model.neuron("active-neuron"));
        assertNull(model.neuron("partial-neuron"));
        assertTrue(mapService.isActiveRepository(activeRepository));
        assertFalse(mapService.isActiveRepository(candidate));
        assertFalse(activeRepository.closed);
        assertFalse(candidate.closed);
    }

    /**
     * Перевіряє, що помилка читання камери не активує кандидата й не публікує його попередньо завантажену карту.
     */
    @Test
    void failedCameraReadDoesNotPublishCandidateModel() {
        NeuronMapModel model = new NeuronMapModel();
        model.addNeuron("active-neuron", NeuronType.EXCITATORY, 1);
        TestMapRepository activeRepository = new TestMapRepository();
        MapService mapService = new MapService(model, activeRepository);
        TestMapRepository candidate = new TestMapRepository();
        candidate.loader = target -> {
            target.clear();
            target.addNeuron("candidate-neuron", NeuronType.INHIBITORY, 0);
        };
        candidate.cameraFailure = new IllegalArgumentException("Некоректний стан камери");

        assertThrows(
                IllegalArgumentException.class,
                () -> mapService.activateRepository(candidate, true, 50.0)
        );

        assertEquals(1, model.neurons().size());
        assertNotNull(model.neuron("active-neuron"));
        assertNull(model.neuron("candidate-neuron"));
        assertTrue(mapService.isActiveRepository(activeRepository));
        assertFalse(activeRepository.closed);
    }

    /**
     * Перевіряє, що помилка читання швидкості не публікує карту кандидата й не змінює активний репозиторій.
     */
    @Test
    void failedSimulationSpeedReadDoesNotPublishCandidateModel() {
        NeuronMapModel model = new NeuronMapModel();
        model.addNeuron("active-neuron", NeuronType.EXCITATORY, 1);
        TestMapRepository activeRepository = new TestMapRepository();
        MapService mapService = new MapService(model, activeRepository);
        TestMapRepository candidate = new TestMapRepository();
        candidate.loader = target -> {
            target.clear();
            target.addNeuron("candidate-neuron", NeuronType.INHIBITORY, 0);
        };
        candidate.tickFailure = new IllegalStateException("Не вдалося прочитати швидкість");

        assertThrows(
                IllegalStateException.class,
                () -> mapService.activateRepository(candidate, true, 50.0)
        );

        assertEquals(1, model.neurons().size());
        assertNotNull(model.neuron("active-neuron"));
        assertNull(model.neuron("candidate-neuron"));
        assertTrue(mapService.isActiveRepository(activeRepository));
        assertFalse(activeRepository.closed);
    }

    /**
     * Перевіряє, що після успішного читання стан публікується, а репозиторій отримує лише тимчасову модель.
     */
    @Test
    void successfulActivationPublishesLoadedStateAndTransfersRepositoryOwnership() {
        NeuronMapModel model = new NeuronMapModel();
        model.addNeuron("active-neuron", NeuronType.EXCITATORY, 1);
        TestMapRepository activeRepository = new TestMapRepository();
        MapService mapService = new MapService(model, activeRepository);
        CameraState cameraState = new CameraState(1.5, 24.0, -18.0);
        TestMapRepository candidate = new TestMapRepository();
        candidate.cameraState = cameraState;
        candidate.simulationTickMillis = 125.0;
        candidate.loader = target -> {
            target.clear();
            target.addNeuron("candidate-neuron", NeuronType.INHIBITORY, 0);
        };

        MapService.ProjectActivationState state = mapService.activateRepository(candidate, true, 50.0);

        assertSame(cameraState, state.cameraState());
        assertEquals(125.0, state.simulationTickMillis(), 0.0001);
        assertTrue(mapService.isActiveRepository(candidate));
        assertTrue(activeRepository.closed);
        assertNotSame(model, candidate.loadedModel);
        assertEquals(1, model.neurons().size());
        assertNotNull(model.neuron("candidate-neuron"));
        assertNull(model.neuron("active-neuron"));
    }

    /**
     * Перевіряє, що новий тимчасовий проєкт не читає збережені камеру й швидкість, а використовує типові значення.
     */
    @Test
    void activationOfNewProjectUsesDefaultsWithoutReadingSavedSettings() {
        NeuronMapModel model = new NeuronMapModel();
        TestMapRepository activeRepository = new TestMapRepository();
        MapService mapService = new MapService(model, activeRepository);
        TestMapRepository candidate = new TestMapRepository();
        candidate.cameraFailure = new IllegalStateException("Камера не повинна читатися");
        candidate.tickFailure = new IllegalStateException("Швидкість не повинна читатися");

        MapService.ProjectActivationState state = mapService.activateRepository(candidate, false, 50.0);

        assertEquals(CameraState.defaultState(), state.cameraState());
        assertEquals(50.0, state.simulationTickMillis(), 0.0001);
        assertTrue(mapService.isActiveRepository(candidate));
    }

    /**
     * Перевіряє, що помилка закриття старого репозиторію не робить кандидат активним.
     */
    @Test
    void failedCloseDoesNotActivateReplacementRepository() {
        NeuronMapModel model = new NeuronMapModel();
        TestMapRepository activeRepository = new TestMapRepository();
        activeRepository.closeFailure = new IllegalStateException("Не вдалося закрити старе сховище");
        MapService mapService = new MapService(model, activeRepository);
        TestMapRepository candidate = new TestMapRepository();

        assertThrows(RuntimeException.class, () -> mapService.switchRepository(candidate));

        assertFalse(mapService.isActiveRepository(activeRepository));
        assertFalse(mapService.isActiveRepository(candidate));
        assertThrows(IllegalStateException.class, mapService::databasePath);
        mapService.close();
        assertTrue(activeRepository.closed);
        assertEquals(1, activeRepository.closeCount);
    }

    /**
     * Репозиторій тесту дає змогу керувати результатом читання та перевіряти життєвий цикл ресурсів.
     */
    private static final class TestMapRepository implements MapRepository {

        private Consumer<NeuronMapModel> loader = model -> model.clear();
        private CameraState cameraState = CameraState.defaultState();
        private double simulationTickMillis = 75.0;
        private RuntimeException loadFailure;
        private RuntimeException cameraFailure;
        private RuntimeException tickFailure;
        private RuntimeException closeFailure;
        private boolean closed;
        private int closeCount;
        private NeuronMapModel loadedModel;

        /** Створює ізольований репозиторій зі значеннями за замовчуванням для тесту. */
        private TestMapRepository() {
        }

        /**
         * Повертає шлях-заповнювач, оскільки ці тести не працюють із файлами.
         *
         * @return стабільний тестовий шлях.
         */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /**
         * Повертає налаштований стан камери або відтворює помилку читання.
         *
         * @return стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            if (cameraFailure != null) {
                throw cameraFailure;
            }
            return cameraState;
        }

        /**
         * Повертає налаштовану тривалість такту або відтворює помилку читання.
         *
         * @param fallbackMillis резервне значення, яке надає служба карти.
         * @return збережена тривалість такту.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            if (tickFailure != null) {
                throw tickFailure;
            }
            return simulationTickMillis;
        }

        /**
         * Завантажує дані в передану модель і за потреби імітує помилку після часткової зміни.
         *
         * @param model тимчасова модель, надана службою карти.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
            loadedModel = model;
            if (loadFailure != null) {
                model.clear();
                model.addNeuron("partial-neuron", NeuronType.EXCITATORY, 0);
                throw loadFailure;
            }
            loader.accept(model);
        }

        /** Не зберігає швидкість, оскільки тести перевіряють лише сценарії читання. */
        @Override
        public void saveSimulationTickMillis(double millis) {
        }

        /** Не зберігає карту, оскільки тести не створюють файлів. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
        }

        /**
         * Позначає репозиторій закритим або відтворює задану помилку очищення ресурсу.
         */
        @Override
        public void close() {
            closed = true;
            closeCount++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
