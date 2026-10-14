package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.MapService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє збереження швидкості в активному проєкті та обробку некоректного введення. */
class SimulationSpeedCoordinatorTest {

    private static final double MIN_TICK_MILLIS = 10.0;
    private static final double MAX_TICK_MILLIS = 500.0;

    /** Перевіряє, що зміна швидкості записується лише до репозиторію активного проєкту. */
    @Test
    void savesSpeedToCurrentlyActiveProjectBeforeApplyingIt() {
        InMemoryMapRepository firstRepository = new InMemoryMapRepository(
                Path.of("first-project.db"), 40.0, true
        );
        MapService mapService = new MapService(new NeuronMapModel(), firstRepository);
        double[] appliedSpeed = {Double.NaN};
        AtomicReference<String> status = new AtomicReference<>();
        int[] metadataRefreshes = {0};
        SimulationSpeedCoordinator coordinator = createCoordinator(
                mapService,
                appliedSpeed,
                status,
                metadataRefreshes
        );

        InMemoryMapRepository secondRepository = new InMemoryMapRepository(
                Path.of("second-project.db"), 80.0, true
        );
        mapService.switchRepository(secondRepository);

        coordinator.changeSpeed("125");

        assertEquals(List.of(), firstRepository.savedSpeeds());
        assertTrue(firstRepository.isClosed());
        assertEquals(List.of(125.0), secondRepository.savedSpeeds());
        assertEquals(125.0, secondRepository.loadSimulationTickMillis(0.0), 0.0001);
        assertEquals(125.0, appliedSpeed[0], 0.0001);
        assertEquals(1, metadataRefreshes[0]);
        assertNull(status.get());
    }

    /** Перевіряє, що некоректна швидкість не змінює ані сховище, ані активну симуляцію. */
    @Test
    void invalidSpeedDoesNotPersistOrApplyAValue() {
        InMemoryMapRepository repository = new InMemoryMapRepository(
                Path.of("project.db"), 35.0, true
        );
        MapService mapService = new MapService(new NeuronMapModel(), repository);
        double[] appliedSpeed = {35.0};
        AtomicReference<String> status = new AtomicReference<>();
        int[] metadataRefreshes = {0};
        SimulationSpeedCoordinator coordinator = createCoordinator(
                mapService,
                appliedSpeed,
                status,
                metadataRefreshes
        );
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);

        coordinator.changeSpeed("not-a-number");

        assertTrue(repository.savedSpeeds().isEmpty());
        assertEquals(35.0, appliedSpeed[0], 0.0001);
        assertEquals(localization.text("status.speed_invalid"), status.get());
        assertEquals(0, metadataRefreshes[0]);
    }

    /** Перевіряє, що тимчасовий репозиторій не запускає оновлення метаданих збереженого проєкту. */
    @Test
    void transientRepositorySkipsPersistentProjectMetadataUpdate() {
        InMemoryMapRepository repository = new InMemoryMapRepository(
                Path.of("temporary-project.db"), 25.0, false
        );
        MapService mapService = new MapService(new NeuronMapModel(), repository);
        double[] appliedSpeed = {Double.NaN};
        AtomicReference<String> status = new AtomicReference<>();
        int[] metadataRefreshes = {0};
        SimulationSpeedCoordinator coordinator = createCoordinator(
                mapService,
                appliedSpeed,
                status,
                metadataRefreshes
        );

        coordinator.changeSpeed("55");

        assertEquals(List.of(55.0), repository.savedSpeeds());
        assertEquals(55.0, appliedSpeed[0], 0.0001);
        assertEquals(0, metadataRefreshes[0]);
        assertFalse(repository.isClosed());
        assertNull(status.get());
    }

    /** Створює координатор із тестовими діями застосування швидкості та оновлення метаданих. */
    private static SimulationSpeedCoordinator createCoordinator(
            MapService mapService,
            double[] appliedSpeed,
            AtomicReference<String> status,
            int[] metadataRefreshes
    ) {
        return new SimulationSpeedCoordinator(
                mapService,
                millis -> appliedSpeed[0] = millis,
                () -> metadataRefreshes[0]++,
                status::set,
                new LocalizationService(Locale.ENGLISH),
                MIN_TICK_MILLIS,
                MAX_TICK_MILLIS
        );
    }

    /** Тестове сховище, яке записує зміни швидкості окремо для кожного екземпляра репозиторію. */
    private static final class InMemoryMapRepository implements MapRepository {

        private final Path path;
        private final boolean persistent;
        private final List<Double> savedSpeeds = new ArrayList<>();
        private double speed;
        private boolean closed;

        /** Створює сховище з власним початковим значенням швидкості. */
        private InMemoryMapRepository(Path path, double speed, boolean persistent) {
            this.path = path;
            this.speed = speed;
            this.persistent = persistent;
        }

        /** Повертає шлях, який ідентифікує тестовий проєкт. */
        @Override
        public Path databasePath() {
            return path;
        }

        /** Повертає, чи імітує репозиторій постійне збереження. */
        @Override
        public boolean isPersistent() {
            return persistent;
        }

        /** Повертає стандартну камеру, оскільки камера не є предметом цього тесту. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Повертає збережену швидкість або резервне значення, якщо поточне значення некоректне. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return Double.isFinite(speed) && speed > 0.0 ? speed : fallbackMillis;
        }

        /** Залишає модель без змін, бо перевірка стосується лише швидкості. */
        @Override
        public void loadInto(NeuronMapModel model) {
            // Тест не завантажує вміст карти.
        }

        /** Записує нову швидкість у конкретний екземпляр тестового репозиторію. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            speed = millis;
            savedSpeeds.add(millis);
        }

        /** Не зберігає карту, оскільки тест перевіряє лише параметр швидкості. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Тест не зберігає модель карти.
        }

        /** Позначає звільнення ресурсу після заміни репозиторію. */
        @Override
        public void close() {
            closed = true;
        }

        /** Повертає всі значення швидкості, записані під час тесту. */
        private List<Double> savedSpeeds() {
            return List.copyOf(savedSpeeds);
        }

        /** Перевіряє, чи закрили репозиторій під час заміни. */
        private boolean isClosed() {
            return closed;
        }
    }
}
