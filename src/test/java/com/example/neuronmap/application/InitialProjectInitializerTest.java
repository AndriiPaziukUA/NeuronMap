package com.example.neuronmap.application;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє правила початкового наповнення тимчасового та збереженого проєктів.
 */
final class InitialProjectInitializerTest {

    /** Перевіряє створення демонстраційної карти для нового порожнього тимчасового проєкту. */
    @Test
    void initializesEmptyTransientProjectWithDemoMap() {
        NeuronMapModel model = new NeuronMapModel();
        InitialProjectInitializer initializer = createInitializer(model, false);

        boolean initialized = initializer.initializeIfRequired(transientProject());

        assertTrue(initialized);
        assertEquals(3, model.neurons().size());
        assertEquals(2, model.connections().size());
    }

    /** Перевіряє, що наявні дані тимчасового проєкту не доповнюються демо-картою. */
    @Test
    void leavesNonEmptyTransientProjectUnchanged() {
        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService application = new NeuronMapApplicationService(model, new TestMapRepository(false));
        application.neurons().create(NeuronType.INHIBITORY, 40.0, 60.0);
        InitialProjectInitializer initializer = new InitialProjectInitializer(application);

        boolean initialized = initializer.initializeIfRequired(transientProject());

        assertFalse(initialized);
        assertEquals(1, model.neurons().size());
        assertTrue(model.connections().isEmpty());
    }

    /** Перевіряє, що порожній, але вже збережений проєкт не отримує демонстраційних даних. */
    @Test
    void leavesEmptyPersistedProjectUnchanged() {
        NeuronMapModel model = new NeuronMapModel();
        InitialProjectInitializer initializer = createInitializer(model, true);

        boolean initialized = initializer.initializeIfRequired(persistedProject());

        assertFalse(initialized);
        assertTrue(model.isEmpty());
    }

    /** Перевіряє, що сценарій ініціалізації вимагає опис поточного проєкту. */
    @Test
    void rejectsMissingProjectDescriptor() {
        InitialProjectInitializer initializer = createInitializer(new NeuronMapModel(), false);

        assertThrows(NullPointerException.class, () -> initializer.initializeIfRequired(null));
    }

    /**
     * Створює сценарій ініціалізації, ізольований тестовою моделлю та репозиторієм.
     *
     * @param model модель, стан якої перевіряється в тесті.
     * @return сценарій початкової ініціалізації для переданої моделі.
     */
    private InitialProjectInitializer createInitializer(NeuronMapModel model, boolean persistentRepository) {
        return new InitialProjectInitializer(new NeuronMapApplicationService(
                model, new TestMapRepository(persistentRepository)
        ));
    }

    /** Створює опис нового тимчасового проєкту, який ще не має збереженого стану. */
    private ProjectDescriptor transientProject() {
        return new ProjectDescriptor("transient", "Тимчасовий проєкт", Path.of("transient.db"), null);
    }

    /** Створює опис проєкту, який уже має збережений стан. */
    private ProjectDescriptor persistedProject() {
        return new ProjectDescriptor("persisted", "Збережений проєкт", Path.of("persisted.db"), Instant.EPOCH);
    }

    /**
     * Тестовий репозиторій без файлових операцій, потрібний для створення прикладної служби.
     */
    private static final class TestMapRepository implements MapRepository {

        private final boolean persistent;

        /**
         * Створює тестовий репозиторій із заданою ознакою постійного збереження.
         *
         * @param persistent {@code true}, якщо сценарій перевіряє збережений проєкт.
         */
        private TestMapRepository(boolean persistent) {
            this.persistent = persistent;
        }

        /** Повертає ознаку постійного збереження тестового репозиторію. */
        @Override
        public boolean isPersistent() {
            return persistent;
        }

        /** Повертає стабільний шлях до тестового файла бази даних. */
        @Override
        public Path databasePath() {
            return Path.of("test-project.db");
        }

        /** Повертає типове значення швидкості симуляції, оскільки тест не зберігає налаштування. */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /** Не зберігає швидкість симуляції, оскільки файлова система в цьому тесті не використовується. */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // Запис швидкості не належить до сценаріїв цього тесту.
        }

        /** Повертає стандартний стан камери, оскільки параметри камери тут не перевіряються. */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /** Не змінює модель під час завантаження, щоб тест міг сам задавати її початковий стан. */
        @Override
        public void loadInto(NeuronMapModel model) {
            // Початковий стан моделі задається самим тестом.
        }

        /** Не записує модель до постійного сховища, оскільки репозиторій є тестовою заглушкою. */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Збереження не належить до сценаріїв цього тесту.
        }

        /** Звільняє тестовий репозиторій; ресурсів поза пам'яттю він не утримує. */
        @Override
        public void close() {
            // Тестовий репозиторій не має ресурсів для закриття.
        }
    }
}
