package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.simulation.SimulationSpeed;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Керує життєвим циклом редакторської сесії під час переходу між проєктами та показу меню.
 * Координатор відновлює стан карти й подань, але делегує конкретні дії контролерам і поданням через контракт дій.
 */
public final class EditorProjectSessionCoordinator {

    private final MapService mapService;
    private final HistoryService historyService;
    private final EditorState state;
    private final Consumer<ProjectDescriptor> currentProjectConsumer;
    private final DoubleConsumer simulationTickMillisConsumer;
    private final Consumer<Optional<ProjectDescriptor>> markLastOpened;
    private final double minSimulationTickMillis;
    private final double maxSimulationTickMillis;
    private final double defaultSimulationTickMillis;
    private final SessionActions actions;
    private boolean modalSimulationWasRunning;

    /**
     * Створює координатор сесії з доступом до моделі, стану редактора та потрібних зовнішніх дій.
     *
     * @param mapService служба активної карти й репозиторію.
     * @param historyService служба історії змін редактора.
     * @param state змінний стан камери, вибору та режимів редактора.
     * @param currentProjectConsumer отримувач опису активного проєкту після переходу.
     * @param simulationTickMillisConsumer отримувач нової тривалості такту симуляції.
     * @param markLastOpened дія встановлення або очищення останнього відкритого проєкту; порожній Optional очищує значення.
     * @param minSimulationTickMillis мінімальна допустима тривалість такту.
     * @param maxSimulationTickMillis максимальна допустима тривалість такту.
     * @param defaultSimulationTickMillis типова тривалість такту для проєкту без коректного збереженого значення.
     * @param actions операції контролерів і подань, що супроводжують перехід між станами.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     */
    public EditorProjectSessionCoordinator(
            MapService mapService,
            HistoryService historyService,
            EditorState state,
            Consumer<ProjectDescriptor> currentProjectConsumer,
            DoubleConsumer simulationTickMillisConsumer,
            Consumer<Optional<ProjectDescriptor>> markLastOpened,
            double minSimulationTickMillis,
            double maxSimulationTickMillis,
            double defaultSimulationTickMillis,
            SessionActions actions
    ) {
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.state = Objects.requireNonNull(state, "state");
        this.currentProjectConsumer = Objects.requireNonNull(currentProjectConsumer, "currentProjectConsumer");
        this.simulationTickMillisConsumer = Objects.requireNonNull(
                simulationTickMillisConsumer,
                "simulationTickMillisConsumer"
        );
        this.markLastOpened = Objects.requireNonNull(markLastOpened, "markLastOpened");
        this.minSimulationTickMillis = minSimulationTickMillis;
        this.maxSimulationTickMillis = maxSimulationTickMillis;
        this.defaultSimulationTickMillis = defaultSimulationTickMillis;
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Призупиняє анімації та симуляцію на час відкритого меню, зберігаючи ознаку потреби відновлення.
     */
    public void pauseForMenu() {
        modalSimulationWasRunning = actions.pauseSimulationForModal();
        actions.pauseConnectionAnimations();
    }

    /**
     * Відновлює анімації та поновлює симуляцію лише тоді, коли вона виконувалася перед відкриттям меню.
     */
    public void resumeAfterMenu() {
        actions.resumeConnectionAnimations();
        if (modalSimulationWasRunning) {
            modalSimulationWasRunning = false;
            actions.resumeSimulationFromModal();
        }
    }

    /**
     * Зупиняє тимчасові взаємодії, зберігає поточний проєкт і очищає сесійний стан перед переходом.
     */
    public void prepareForProjectSwitch() {
        actions.stopSimulation();
        actions.clearConnectionHighlights();
        actions.cancelInteractions();
        actions.hideNeuronMenu();
        actions.cancelPendingCameraSave();
        actions.saveCurrentProject();

        state.clearSelection();
        state.resetToIdle();
        modalSimulationWasRunning = false;
    }

    /**
     * Підключає репозиторій проєкту й відновлює модель, камеру, швидкість, історію та подання редактора.
     * Завантаження карти, камери й швидкості виконує {@link MapService} до зміни активного стану сесії.
     * Якщо у вибраного проєкту немає коректної збереженої швидкості, використовує типове значення конфігурації,
     * а не швидкість попереднього проєкту.
     *
     * @param project опис проєкту, який потрібно активувати.
     * @param repository репозиторій даних вибраного проєкту.
     * @param loadExisting ознака потреби відновити збережені налаштування та вміст проєкту.
     * @throws NullPointerException якщо опис проєкту або репозиторій не передано.
     * @throws RuntimeException якщо підготовка репозиторію завершується помилкою.
     */
    public void activateProject(
            ProjectDescriptor project,
            MapRepository repository,
            boolean loadExisting
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(repository, "repository");

        MapService.ProjectActivationState activationState = mapService.activateRepository(
                repository,
                loadExisting,
                defaultSimulationTickMillis
        );
        double speed = resolveInitialSimulationTickMillis(
                activationState.simulationTickMillis(),
                defaultSimulationTickMillis,
                minSimulationTickMillis,
                maxSimulationTickMillis
        );

        currentProjectConsumer.accept(project);
        simulationTickMillisConsumer.accept(speed);
        actions.updateSimulationSpeed(speed);
        restoreCameraAndSelection(activationState.cameraState());
        historyService.clear();
        historyService.initialize();
        actions.synchronizeViewsWithModel();
        actions.applyCamera();
        markLastOpened.accept(project.isPersisted() ? Optional.of(project) : Optional.empty());
        modalSimulationWasRunning = false;
        actions.showMainPage();
    }

    /**
     * Перевіряє збережену тривалість такту й повертає резервне значення, якщо вона поза дозволеним діапазоном.
     * Метод спільно використовується під час первинного складання редактора та активації наступного проєкту.
     *
     * @param persistedMillis значення, прочитане зі знімка стану проєкту.
     * @param fallbackMillis коректна тривалість, яку слід використати за відсутності допустимого значення.
     * @param minMillis мінімальна допустима тривалість такту.
     * @param maxMillis максимальна допустима тривалість такту.
     * @return допустима тривалість такту симуляції.
     */
    static double resolveInitialSimulationTickMillis(
            double persistedMillis,
            double fallbackMillis,
            double minMillis,
            double maxMillis
    ) {
        try {
            return SimulationSpeed.requireMillis(persistedMillis, minMillis, maxMillis);
        } catch (RuntimeException exception) {
            return fallbackMillis;
        }
    }

    /**
     * Відновлює камеру та прибирає вибір і тимчасові режими, які не повинні переходити між проєктами.
     *
     * @param camera стан камери, завантажений службою карти або створений за замовчуванням.
     */
    private void restoreCameraAndSelection(CameraState camera) {
        state.setZoom(camera.zoom());
        state.setPanX(camera.panX());
        state.setPanY(camera.panY());
        state.clearSelection();
        state.resetToIdle();
    }

    /**
     * Визначає зовнішні дії, які координатор виконує під час перемикання проєкту та роботи меню.
     */
    public interface SessionActions {

        /** Зупиняє виконання симуляції перед очищенням редакторської сесії. */
        void stopSimulation();

        /** Прибирає візуальні позначки режиму видалення зв’язків. */
        void clearConnectionHighlights();

        /** Скасовує незавершені взаємодії з картою. */
        void cancelInteractions();

        /** Приховує контекстне меню нейрона, якщо воно відкрите. */
        void hideNeuronMenu();

        /** Скасовує відкладене збереження камери, щоб воно не перезаписало стан іншого проєкту. */
        void cancelPendingCameraSave();

        /** Зберігає поточний проєкт до перемикання репозиторію. */
        void saveCurrentProject();

        /** Призупиняє симуляцію для меню та повертає ознаку її попередньої активності. */
        boolean pauseSimulationForModal();

        /** Відновлює симуляцію, що була призупинена саме для модального меню. */
        void resumeSimulationFromModal();

        /** Призупиняє анімації зв’язків на час відкритого меню. */
        void pauseConnectionAnimations();

        /** Відновлює анімації зв’язків після закриття меню. */
        void resumeConnectionAnimations();

        /**
         * Застосовує швидкість симуляції до контролера та панелі інструментів.
         *
         * @param millis нова тривалість такту симуляції в мілісекундах.
         */
        void updateSimulationSpeed(double millis);

        /** Синхронізує візуальні подання з моделлю нового проєкту. */
        void synchronizeViewsWithModel();

        /** Застосовує відновлені координати камери до робочої області. */
        void applyCamera();

        /** Повертає подання головного меню на початкову сторінку після переходу. */
        void showMainPage();
    }
}
