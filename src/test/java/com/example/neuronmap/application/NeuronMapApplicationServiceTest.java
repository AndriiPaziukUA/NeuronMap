package com.example.neuronmap.application;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Перевіряє створення служб застосунку та передавання операцій між компонентами.
 */
final class NeuronMapApplicationServiceTest {

    /**
     * Перевіряє очікувану поведінку: служби і камера стан.
     */
    @Test
    void exposesSpecializedServicesAndSavesCameraState() {
        FakeRepository repository = new FakeRepository();
        NeuronMapModel model = new NeuronMapModel();
        NeuronMapApplicationService service =
                new NeuronMapApplicationService(model, repository);

        service.map().load();

        Neuron neuron = service.neurons().create(
                NeuronType.EXCITATORY,
                100,
                200
        );

        assertNotNull(neuron);
        assertSame(model, service.neurons().model());
        assertEquals(1, model.neurons().size());

        EditorState state = new EditorState(1.5, -10, 25);
        service.map().save(state);

        assertNotNull(repository.savedModel);
        assertEquals(
                new CameraState(1.5, -10, 25),
                repository.savedCamera
        );
    }

    /**
     * Перевіряє очікувану поведінку: такт карта служба.
     */
    @Test
    void delegatesSimulationTickPersistenceThroughMapService() {
        FakeRepository repository = new FakeRepository();
        NeuronMapApplicationService service =
                new NeuronMapApplicationService(
                        new NeuronMapModel(),
                        repository
                );

        assertEquals(
                123.0,
                service.map().loadSimulationTickMillis(123.0)
        );

        service.map().saveSimulationTickMillis(75.0);

        assertEquals(
                75.0,
                repository.savedSimulationTickMillis
        );
    }

    /**
     * Набір модульних тестів типу FakeRepository. Перевіряє його основну поведінку та обробку некоректних або крайових даних.
     */
    private static final class FakeRepository implements MapRepository {

        private NeuronMapModel savedModel;
        private CameraState savedCamera;
        private double savedSimulationTickMillis = Double.NaN;

        /**
         * Повертає результат операції «база даних шлях».
         *
         * @return шлях до відповідного файлу або каталогу.
         */
        @Override
        public Path databasePath() {
            return Path.of("test.db");
        }

        /**
         * Повертає або знаходить дані, повʼязані з «такт».
         *
         * @param fallbackMillis значення, що визначає резервний варіант для цієї операції.
         *
         * @return числове значення, визначене методом.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /**
         * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
         *
         * @param millis значення, що визначає відповідну операцію для цієї операції.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
            savedSimulationTickMillis = millis;
        }

        /**
         * Повертає або знаходить дані, повʼязані з «камера стан».
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /**
         * Повертає або знаходить дані, повʼязані з «відповідну операцію».
         *
         * @param model модель карти нейронів.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
        }

        /**
         * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
         *
         * @param model модель карти нейронів.
         *
         * @param cameraState значення, що визначає камера стан для цієї операції.
         */
        @Override
        public void save(
                NeuronMapModel model,
                CameraState cameraState
        ) {
            savedModel = model;
            savedCamera = cameraState;
        }

        /**
         * Завершує або скасовує дію, повʼязану з «потрібні дані».
         */
        @Override
        public void close() {
        }
    }
}
