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
     * Завантажує дані активного репозиторію в тимчасову модель і публікує їх лише після успішного читання.
     * У разі помилки поточний стан моделі залишається незмінним.
     *
     * @throws IllegalStateException якщо репозиторій наразі від'єднано.
     * @throws RuntimeException якщо репозиторій не зміг завантажити дані.
     */
    public void load() {
        NeuronMapModel loadedModel = loadModel(activeRepository());
        model.replaceContentsFrom(loadedModel);
    }

    /**
     * Завантажує кандидатний репозиторій, а після успішного читання стану активує його та переносить завантажений вміст у спільну модель.
     * Репозиторій стає власністю цієї служби лише після успішного повернення методу; у разі помилки
     * відповідальність за закриття кандидата залишається у виклику, який його передав.
     *
     * @param candidate репозиторій проєкту, який готується до активації.
     * @param loadExisting чи потрібно відновити збережені камеру та швидкість симуляції.
     * @param fallbackSimulationTickMillis типова тривалість такту для нового проєкту або відсутнього значення.
     * @return знімок стану камери й тривалості такту, отриманий під час підготовки проєкту.
     * @throws NullPointerException якщо кандидат або зчитаний стан камери дорівнює {@code null}.
     * @throws RuntimeException якщо завантаження кандидата або закриття попереднього репозиторію завершується помилкою.
     */
    public ProjectActivationState activateRepository(
            MapRepository candidate,
            boolean loadExisting,
            double fallbackSimulationTickMillis
    ) {
        Objects.requireNonNull(candidate, "candidate");

        NeuronMapModel loadedModel = loadModel(candidate);
        CameraState cameraState = loadExisting
                ? Objects.requireNonNull(candidate.loadCameraState(), "cameraState")
                : CameraState.defaultState();
        double simulationTickMillis = loadExisting
                ? candidate.loadSimulationTickMillis(fallbackSimulationTickMillis)
                : fallbackSimulationTickMillis;

        switchRepository(candidate);
        model.replaceContentsFrom(loadedModel);

        return new ProjectActivationState(cameraState, simulationTickMillis);
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
     * Передає службі новий репозиторій після від'єднання та закриття попереднього.
     * Посилання на попередній репозиторій очищується до закриття, тому його помилка не активує кандидата
     * й не призводить до повторного закриття того самого екземпляра.
     *
     * @param newRepository репозиторій, який має стати активним.
     * @throws RuntimeException якщо закриття попереднього підключеного репозиторію завершилося помилкою;
     *                          у такому разі новий репозиторій залишається власністю виклику.
     */
    public void switchRepository(MapRepository newRepository) {
        Objects.requireNonNull(newRepository, "newRepository");
        if (repository == newRepository) {
            return;
        }

        MapRepository oldRepository = repository;
        repository = null;
        if (oldRepository != null) {
            oldRepository.close();
        }
        repository = newRepository;
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
     * Завантажує дані репозиторію в окрему модель, не змінюючи модель редактора до завершення читання.
     *
     * @param source репозиторій, з якого завантажуються дані.
     * @return модель із повністю завантаженим вмістом репозиторію.
     */
    private NeuronMapModel loadModel(MapRepository source) {
        NeuronMapModel loadedModel = new NeuronMapModel();
        source.loadInto(loadedModel);
        return loadedModel;
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

    /**
     * Містить дані стану, підготовлені під час успішної активації репозиторію проєкту.
     *
     * @param cameraState стан камери, який потрібно відновити в редакторі.
     * @param simulationTickMillis тривалість такту симуляції, прочитана зі сховища або взята за замовчуванням.
     */
    public record ProjectActivationState(CameraState cameraState, double simulationTickMillis) {
    }
}
