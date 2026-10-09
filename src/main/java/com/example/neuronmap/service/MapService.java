package com.example.neuronmap.service;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Координує завантаження й збереження моделі карти, параметрів камери та налаштувань симуляції через репозиторій.
 */
public final class MapService {

    private final NeuronMapModel model;
    private MapRepository repository;

    /**
     * Створює екземпляр MapService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     * @param repository сховище, через яке читають і зберігають карту.
     */
    public MapService(
            NeuronMapModel model,
            MapRepository repository
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Повертає модель карти, якою керує служба збереження.
     *
     * @return модель карти, якою керує служба збереження.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Завантажує збережену модель карти з активного репозиторію.
     */
    public void load() {
        repository.loadInto(model);
    }

    /**
     * Перевіряє, чи empty за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isEmpty() {
        return model.isEmpty();
    }

    /**
     * Перевіряє, чи persistent за поточного стану компонента.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean isPersistent() {
        return repository.isPersistent();
    }

    /**
     * Завантажує camera state із відповідного джерела даних.
     */
    public CameraState loadCameraState() {
        return repository.loadCameraState();
    }

    /**
     * Завантажує simulation tick millis із відповідного джерела даних.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     */
    public double loadSimulationTickMillis(double fallbackMillis) {
        return repository.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Повертає шлях до файлу бази даних.
     *
     * @return шлях до файлу бази даних.
     */
    public Path databasePath() {
        return repository.databasePath();
    }

    /**
     * Зберігає карту та параметри камери через активний репозиторій.
     *
     * @param state стан об’єкта, який потрібно зберегти або відновити.
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
     * Зберігає simulation tick millis у відповідному сховищі.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void saveSimulationTickMillis(double millis) {
        repository.saveSimulationTickMillis(millis);
    }

/**
 * Перемикає активний репозиторій і закриває попереднє сховище.
 *
 * @param newRepository значення «new repository», яке використовується в цьому методі.
 */
public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");

        MapRepository oldRepository = repository;
        repository = newRepository;
        oldRepository.close();
    }

    /**
     * Закриває активний репозиторій карти та звільняє його ресурси.
     */
    public void close() {
        repository.close();
    }
}
