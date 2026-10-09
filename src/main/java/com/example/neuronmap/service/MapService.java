package com.example.neuronmap.service;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Надає операції завантаження, зміни та збереження карти через контракт сховища.
 */
public final class MapService {

    private final NeuronMapModel model;
    private MapRepository repository;

    /**
     * Повертає результат операції «карта служба».
     *
     * @param model модель карти нейронів.
     *
     * @param repository значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MapService(
            NeuronMapModel model,
            MapRepository repository
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Повертає результат операції «модель».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     */
    public void load() {
        repository.loadInto(model);
    }

    /**
     * Перевіряє, чи виконується умова «порожній».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isEmpty() {
        return model.isEmpty();
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isPersistent() {
        return repository.isPersistent();
    }

    /**
     * Повертає або знаходить дані, повʼязані з «камера стан».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public CameraState loadCameraState() {
        return repository.loadCameraState();
    }

    /**
     * Повертає або знаходить дані, повʼязані з «такт».
     *
     * @param fallbackMillis значення, що визначає резервний варіант для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    public double loadSimulationTickMillis(double fallbackMillis) {
        return repository.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Повертає результат операції «база даних шлях».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    public Path databasePath() {
        return repository.databasePath();
    }

    /**
     * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
     *
     * @param state стан обʼєкта або редактора.
     */
    public void save(EditorState state) {
        Objects.requireNonNull(state, "state");
        repository.save(
                model,
                new CameraState(
                        state.zoom(),
                        state.panX(),
                        state.panY()
                )
        );
    }

    /**
     * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     */
    public void saveSimulationTickMillis(double millis) {
        repository.saveSimulationTickMillis(millis);
    }

/**
 * Перемикає стан «відповідну операцію».
 *
 * @param newRepository значення, що визначає новий для цієї операції.
 */
public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");

        MapRepository oldRepository = repository;
        repository = newRepository;
        oldRepository.close();
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void close() {
        repository.close();
    }
}
