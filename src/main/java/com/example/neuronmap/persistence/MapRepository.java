package com.example.neuronmap.persistence;

import com.example.neuronmap.model.NeuronMapModel;

import java.nio.file.Path;

/**
 * Визначає контракт читання та збереження карти без привʼязки до конкретного сховища.
 */
public interface MapRepository extends AutoCloseable {

    /**
     * Повертає результат операції «база даних шлях».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    Path databasePath();

/**
 * Перевіряє, чи виконується умова «відповідну операцію».
 *
 * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
 */
default boolean isPersistent() {
        return true;
    }

    /**
     * Повертає або знаходить дані, повʼязані з «камера стан».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    CameraState loadCameraState();

    /**
     * Повертає або знаходить дані, повʼязані з «такт».
     *
     * @param fallbackMillis значення, що визначає резервний варіант для цієї операції.
     *
     * @return числове значення, визначене методом.
     */
    double loadSimulationTickMillis(double fallbackMillis);

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param model модель карти нейронів.
     */
    void loadInto(NeuronMapModel model);

    /**
     * Зберігає дані, повʼязані з «такт», у відповідному сховищі.
     *
     * @param millis значення, що визначає відповідну операцію для цієї операції.
     */
    void saveSimulationTickMillis(double millis);

    /**
     * Зберігає дані, повʼязані з «потрібні дані», у відповідному сховищі.
     *
     * @param model модель карти нейронів.
     *
     * @param cameraState значення, що визначає камера стан для цієї операції.
     */
    void save(
            NeuronMapModel model,
            CameraState cameraState
    );

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    @Override
    void close();
}
