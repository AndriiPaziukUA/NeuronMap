package com.example.neuronmap.service;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Координує читання й запис моделі карти, стану камери та параметрів симуляції через активний репозиторій.
 */
public final class MapService {

    private final NeuronMapModel model;
    private MapRepository repository;

    /**
     * Створює службу карти з моделлю та сховищем, через яке виконуються операції збереження.
     *
     * @param model модель карти нейронів.
     * @param repository репозиторій, що зберігає дані карти.
     */
    public MapService(NeuronMapModel model, MapRepository repository) {
        this.model = Objects.requireNonNull(model, "model");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Повертає модель карти, якою керує ця служба.
     *
     * @return модель карти нейронів.
     */
    public NeuronMapModel model() {
        return model;
    }

    /**
     * Завантажує дані активного репозиторію до моделі карти.
     */
    public void load() {
        repository.loadInto(model);
    }

    /**
     * Перевіряє, чи не містить модель даних карти.
     *
     * @return {@code true}, якщо модель порожня; інакше {@code false}.
     */
    public boolean isEmpty() {
        return model.isEmpty();
    }

    /**
     * Перевіряє, чи активне сховище підтримує постійне збереження даних.
     *
     * @return {@code true}, якщо активний репозиторій є постійним; інакше {@code false}.
     */
    public boolean isPersistent() {
        return repository.isPersistent();
    }

    /**
     * Перевіряє, чи є переданий репозиторій саме тим екземпляром, який наразі підключено до служби карти.
     *
     * @param candidate репозиторій, володіння яким потрібно перевірити.
     * @return {@code true}, якщо активним є той самий екземпляр; інакше {@code false}.
     */
    public boolean isActiveRepository(MapRepository candidate) {
        return repository == candidate;
    }

    /**
     * Завантажує збережений стан камери з активного репозиторію.
     *
     * @return стан камери, відновлений зі сховища.
     */
    public CameraState loadCameraState() {
        return repository.loadCameraState();
    }

    /**
     * Завантажує збережену тривалість такту симуляції.
     *
     * @param fallbackMillis резервна тривалість такту, якщо в сховищі немає допустимого значення.
     * @return збережена тривалість такту або передане резервне значення.
     */
    public double loadSimulationTickMillis(double fallbackMillis) {
        return repository.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Повертає шлях до файла бази даних активного проєкту.
     *
     * @return шлях до файла бази даних.
     */
    public Path databasePath() {
        return repository.databasePath();
    }

    /**
     * Зберігає поточну модель карти разом зі станом камери.
     *
     * @param cameraState стан камери, який потрібно зберегти.
     */
    public void save(CameraState cameraState) {
        Objects.requireNonNull(cameraState, "cameraState");
        repository.save(model, cameraState);
    }

    /**
     * Зберігає тривалість такту симуляції в активному репозиторії.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void saveSimulationTickMillis(double millis) {
        repository.saveSimulationTickMillis(millis);
    }

    /**
     * Замінює активний репозиторій і закриває попереднє сховище.
     *
     * @param newRepository репозиторій, який має стати активним.
     */
    public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");

        MapRepository oldRepository = repository;
        repository = newRepository;
        oldRepository.close();
    }

    /**
     * Закриває активний репозиторій і звільняє його ресурси.
     */
    public void close() {
        repository.close();
    }
}
