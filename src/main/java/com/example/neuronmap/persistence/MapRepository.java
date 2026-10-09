package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;

/**
 * Визначає контракт сховища карти нейронів, налаштувань камери й швидкості симуляції.
 */
public interface MapRepository extends AutoCloseable {

    /**
     * Повертає шлях до файлу бази даних, з яким працює репозиторій.
     *
     * @return шлях до файлу бази даних, з яким працює репозиторій.
     */
    Path databasePath();

/**
 * Повертає ознаку того, чи зберігає репозиторій дані постійно, а не лише протягом сеансу.
 *
 * @return {@code true}, якщо умову виконано; інакше {@code false}.
 */
default boolean isPersistent() {
        return true;
    }

    /**
     * Завантажує з репозиторію збережений масштаб і зміщення камери.
     */
    CameraState loadCameraState();

    /**
     * Завантажує тривалість такту симуляції або повертає передане резервне значення, якщо налаштування недоступне.
     *
     * @param fallbackMillis резервна тривалість такту, якщо збереженого значення немає.
     */
    double loadSimulationTickMillis(double fallbackMillis);

    /**
     * Завантажує збережені дані в передану модель карти.
     *
     * @param model модель карти нейронів.
     */
    void loadInto(NeuronMapModel model);

    /**
     * Зберігає тривалість такту симуляції.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    void saveSimulationTickMillis(double millis);

    /**
     * Зберігає модель карти та стан камери.
     *
     * @param model модель карти нейронів.
     * @param cameraState стан камери, який потрібно зберегти разом із картою.
     */
    void save(
            NeuronMapModel model,
            CameraState cameraState
    );

    /**
     * Закриває репозиторій і звільняє пов’язані ресурси.
     */
    @Override
    void close();
}
