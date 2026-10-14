package com.example.neuronmap.service;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Координує читання й запис моделі карти, стану камери та параметрів симуляції через підключений репозиторій.
 * Служба є єдиним власником життєвого циклу активного репозиторію.
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
     * Завантажує дані підключеного репозиторію до моделі карти.
     *
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public void load() {
        activeRepository().loadInto(model);
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
     * Перевіряє, чи підключене сховище підтримує постійне збереження даних.
     *
     * @return {@code true}, якщо активний репозиторій є постійним; інакше {@code false}.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public boolean isPersistent() {
        return activeRepository().isPersistent();
    }

    /**
     * Перевіряє, чи є переданий репозиторій саме тим екземпляром, який наразі підключено до служби карти.
     *
     * @param candidate репозиторій, володіння яким потрібно перевірити.
     * @return {@code true}, якщо активним є той самий непорожній екземпляр; інакше {@code false}.
     */
    public boolean isActiveRepository(MapRepository candidate) {
        return candidate != null && repository == candidate;
    }

    /**
     * Завантажує збережений стан камери з підключеного репозиторію.
     *
     * @return стан камери, відновлений зі сховища.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public CameraState loadCameraState() {
        return activeRepository().loadCameraState();
    }

    /**
     * Завантажує збережену тривалість такту симуляції.
     *
     * @param fallbackMillis резервна тривалість такту, якщо в сховищі немає допустимого значення.
     * @return збережена тривалість такту або передане резервне значення.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public double loadSimulationTickMillis(double fallbackMillis) {
        return activeRepository().loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Повертає шлях до файла бази даних підключеного проєкту.
     *
     * @return шлях до файла бази даних.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public Path databasePath() {
        return activeRepository().databasePath();
    }

    /**
     * Зберігає поточну модель карти разом зі станом камери.
     *
     * @param cameraState стан камери, який потрібно зберегти.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public void save(CameraState cameraState) {
        Objects.requireNonNull(cameraState, "cameraState");
        activeRepository().save(model, cameraState);
    }

    /**
     * Зберігає тривалість такту симуляції в підключеному репозиторії.
     *
     * @param millis тривалість такту в мілісекундах.
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     */
    public void saveSimulationTickMillis(double millis) {
        activeRepository().saveSimulationTickMillis(millis);
    }

    /**
     * Підключає новий репозиторій і закриває попередній, якщо він досі був підключений.
     * Від'єднаний раніше репозиторій не закривається повторно.
     *
     * @param newRepository репозиторій, який має стати активним.
     * @throws RuntimeException якщо закриття попереднього підключеного репозиторію завершилося помилкою.
     */
    public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");

        MapRepository oldRepository = repository;
        repository = newRepository;
        if (oldRepository != null && oldRepository != newRepository) {
            oldRepository.close();
        }
    }

    /**
     * Від'єднує поточний репозиторій перед файловою операцією та закриває його рівно один раз.
     * Посилання очищується до закриття, тому повторний виклик або подальше підключення не закриє його знову.
     *
     * @throws RuntimeException якщо закриття репозиторію завершилося помилкою.
     */
    public void detachRepository() {
        MapRepository detachedRepository = repository;
        repository = null;
        if (detachedRepository != null) {
            detachedRepository.close();
        }
    }

    /**
     * Закриває підключений репозиторій і звільняє його ресурси; повторне закриття є безпечною операцією без дії.
     *
     * @throws RuntimeException якщо звільнення ресурсів підключеного репозиторію завершилося помилкою.
     */
    public void close() {
        detachRepository();
    }

    /**
     * Повертає підключений репозиторій або повідомляє, що служба тимчасово не має доступного сховища.
     *
     * @return активний репозиторій карти.
     * @throws IllegalStateException якщо репозиторій від'єднано.
     */
    private MapRepository activeRepository() {
        if (repository == null) {
            throw new IllegalStateException("Репозиторій карти наразі не підключено.");
        }
        return repository;
    }
}
