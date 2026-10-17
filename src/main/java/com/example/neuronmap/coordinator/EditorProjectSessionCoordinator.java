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
 * Керує життєвим циклом редакторської сесії, переходами між проєктами й призупиненням UI на час модального меню.
 * Координатор відновлює стан карти, а спільне керування часовими процесами делегує {@link UiProcessCoordinator}.
 */
public final class EditorProjectSessionCoordinator {

    private static final String MAIN_MENU_SUSPENSION = "main-menu";

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
    private final UiProcessCoordinator uiProcesses = new UiProcessCoordinator();
    private boolean modalSimulationWasRunning;

    /**
     * Створює координатор сесії, реєструє симуляцію та анімації зв'язків у спільному центрі паузи.
     *
     * @param mapService служба активної карти й репозиторію.
     * @param historyService служба історії змін редактора.
     * @param state змінний стан камери, вибору та режимів редактора.
     * @param currentProjectConsumer отримувач опису активного проєкту після переходу.
     * @param simulationTickMillisConsumer отримувач нової тривалості такту симуляції.
     * @param markLastOpened дія встановлення або очищення останнього відкритого проєкту.
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
                simulationTickMillisConsumer, "simulationTickMillisConsumer");
        this.markLastOpened = Objects.requireNonNull(markLastOpened, "markLastOpened");
        this.minSimulationTickMillis = minSimulationTickMillis;
        this.maxSimulationTickMillis = maxSimulationTickMillis;
        this.defaultSimulationTickMillis = defaultSimulationTickMillis;
        this.actions = Objects.requireNonNull(actions, "actions");

        uiProcesses.register(
                "simulation",
                () -> modalSimulationWasRunning = this.actions.pauseSimulationForModal(),
                () -> {
                    if (modalSimulationWasRunning) {
                        modalSimulationWasRunning = false;
                        this.actions.resumeSimulationFromModal();
                    }
                }
        );
        uiProcesses.register(
                "connection-animations",
                this.actions::pauseConnectionAnimations,
                this.actions::resumeConnectionAnimations
        );
    }

    /** Призупиняє всі зареєстровані UI-процеси на час відкритого головного меню. */
    public void pauseForMenu() {
        uiProcesses.suspend(MAIN_MENU_SUSPENSION);
    }

    /** Відновлює UI-процеси після закриття меню, коли не залишилося інших причин паузи. */
    public void resumeAfterMenu() {
        uiProcesses.resume(MAIN_MENU_SUSPENSION);
    }

    /**
     * Реєструє додатковий процес UI у спільному центрі призупинення редактора.
     *
     * @param id унікальний ідентифікатор процесу.
     * @param pause дія зупинки процесу зі збереженням його стану.
     * @param resume дія відновлення процесу після зняття всіх причин паузи.
     * @return дескриптор реєстрації, який від'єднує процес під час закриття.
     */
    public UiProcessCoordinator.Registration registerUiProcess(
            String id,
            Runnable pause,
            Runnable resume
    ) {
        return uiProcesses.register(id, pause, resume);
    }

    /**
     * Призупиняє всі зареєстровані UI-процеси з іменованої причини.
     * Повторна причина не призупиняє процеси повторно.
     *
     * @param reason ідентифікатор причини призупинення.
     */
    public void suspendUiProcesses(String reason) {
        uiProcesses.suspend(reason);
    }

    /** Знімає причину призупинення UI-процесів і відновлює їх лише після зняття останньої причини. */
    public void resumeUiProcesses(String reason) {
        uiProcesses.resume(reason);
    }

    /** Завершує центр паузи під час вимкнення редактора, не запускаючи зупинені процеси повторно. */
    public void disposeUiProcesses() {
        uiProcesses.dispose();
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
     * Якщо у вибраного проєкту немає коректної збереженої швидкості, використовує типове значення конфігурації.
     *
     * @param project опис проєкту, який потрібно активувати.
     * @param repository репозиторій даних вибраного проєкту.
     * @param loadExisting ознака потреби відновити збережені налаштування та вміст проєкту.
     * @throws NullPointerException якщо опис проєкту або репозиторій не передано.
     * @throws RuntimeException якщо підготовка репозиторію завершується помилкою.
     */
    public void activateProject(ProjectDescriptor project, MapRepository repository, boolean loadExisting) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(repository, "repository");
        MapService.ProjectActivationState activationState = mapService.activateRepository(
                repository, loadExisting, defaultSimulationTickMillis);
        double speed = resolveInitialSimulationTickMillis(
                activationState.simulationTickMillis(), defaultSimulationTickMillis,
                minSimulationTickMillis, maxSimulationTickMillis);

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

    /** Відновлює камеру й очищає вибір та тимчасові режими, які не повинні переходити між проєктами. */
    private void restoreCameraAndSelection(CameraState camera) {
        state.setZoom(camera.zoom());
        state.setPanX(camera.panX());
        state.setPanY(camera.panY());
        state.clearSelection();
        state.resetToIdle();
    }

    /** Визначає зовнішні дії для переходу між проєктами та керування станом редактора. */
    public interface SessionActions {

        /** Зупиняє виконання симуляції перед очищенням редакторської сесії. */
        void stopSimulation();

        /** Прибирає візуальні позначки режиму видалення зв'язків. */
        void clearConnectionHighlights();

        /** Скасовує незавершені взаємодії з картою. */
        void cancelInteractions();

        /** Приховує контекстне меню нейрона, якщо воно відкрите. */
        void hideNeuronMenu();

        /** Скасовує відкладене збереження камери перед перемиканням репозиторію. */
        void cancelPendingCameraSave();

        /** Зберігає поточний проєкт до перемикання сховища. */
        void saveCurrentProject();

        /** Призупиняє симуляцію для меню та повертає ознаку її попередньої активності. */
        boolean pauseSimulationForModal();

        /** Відновлює симуляцію, яку було призупинено саме для модального меню. */
        void resumeSimulationFromModal();

        /** Призупиняє анімації зв'язків на час відкритого меню. */
        void pauseConnectionAnimations();

        /** Відновлює анімації зв'язків після закриття меню. */
        void resumeConnectionAnimations();

        /**
         * Застосовує швидкість симуляції до контролера й панелі інструментів.
         *
         * @param millis тривалість такту симуляції в мілісекундах.
         */
        void updateSimulationSpeed(double millis);

        /** Синхронізує візуальні подання з моделлю нового проєкту. */
        void synchronizeViewsWithModel();

        /** Застосовує відновлені координати камери до робочої області. */
        void applyCamera();

        /** Повертає меню на початкову сторінку після переходу між проєктами. */
        void showMainPage();
    }
}
