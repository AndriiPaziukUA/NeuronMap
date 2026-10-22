package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationRuntimeState;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.simulation.SimulationSession;
import com.example.neuronmap.simulation.SimulationSpeed;
import com.example.neuronmap.simulation.SimulationStep;
import com.example.neuronmap.util.NumberDisplayFormatter;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Consumer;

/** Керує життєвим циклом симуляції, таймерами, ручною паузою та передаванням стану симуляції до інтерфейсу. */
public final class SimulationController {

    private static final double MINIMUM_ANIMATION_MILLIS = 0.1;

    private final NeuronService neuronService;
    private final SimulationService simulationService;
    private final SimulationRuntimeState runtimeState;
    private final SimulationStepPresenter stepPresenter;
    private final Runnable save;
    private final Consumer<String> status;
    private final Consumer<Boolean> pausedStateConsumer;
    private final Consumer<Boolean> simulationActivityConsumer;
    private final LocalizationService localization;
    private final double minTickMillis;
    private final double maxTickMillis;
    private final PauseTransition finishDelay;

    private Timeline timeline;
    private SimulationSession session;
    private SimulationStep lastAppliedStep;
    private double tickMillis;
    private boolean paused;
    private boolean modalSuspended;

    /**
     * Створює контролер симуляції зі спільною службою локалізації та перевіреними часовими параметрами.
     *
     * @param neuronService служба операцій над нейронами.
     * @param simulationService служба створення сеансів симуляції.
     * @param stepPresenter компонент відображення результату такту.
     * @param save дія збереження стану.
     * @param status дія показу повідомлень.
     * @param pausedStateConsumer отримувач стану ручної паузи для панелі інструментів.
     * @param simulationActivityConsumer отримувач ознаки активності симуляції.
     * @param initialTickMillis початкова тривалість такту в мілісекундах.
     * @param minTickMillis мінімальна допустима тривалість такту.
     * @param maxTickMillis максимальна допустима тривалість такту.
     * @param localization обов'язкова спільна служба локалізації інтерфейсу.
     * @throws NullPointerException якщо обов'язкова залежність відсутня.
     */
    public SimulationController(
            NeuronService neuronService,
            SimulationService simulationService,
            SimulationStepPresenter stepPresenter,
            Runnable save,
            Consumer<String> status,
            Consumer<Boolean> pausedStateConsumer,
            Consumer<Boolean> simulationActivityConsumer,
            double initialTickMillis,
            double minTickMillis,
            double maxTickMillis,
            LocalizationService localization
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.simulationService = Objects.requireNonNull(simulationService, "simulationService");
        runtimeState = new SimulationRuntimeState(neuronService.model());
        this.stepPresenter = Objects.requireNonNull(stepPresenter, "stepPresenter");
        this.save = Objects.requireNonNull(save, "save");
        this.status = Objects.requireNonNull(status, "status");
        this.pausedStateConsumer = Objects.requireNonNull(pausedStateConsumer, "pausedStateConsumer");
        this.simulationActivityConsumer = Objects.requireNonNull(
                simulationActivityConsumer, "simulationActivityConsumer");
        this.minTickMillis = minTickMillis;
        this.maxTickMillis = maxTickMillis;
        tickMillis = SimulationSpeed.requireMillis(initialTickMillis, minTickMillis, maxTickMillis);
        stepPresenter.setTickDurationMillis(tickMillis);
        finishDelay = new PauseTransition(Duration.millis(tickMillis));

        finishDelay.setOnFinished(event -> finishDelayElapsed());

        simulationActivityConsumer.accept(false);
        pausedStateConsumer.accept(false);
    }

    /** Активує початковий нейрон або виконує повторний ручний запуск у межах поточного такту. */
    public void emitPulse(String sourceNeuronId) {
        if (modalSuspended) {
            return;
        }
        if (session == null) {
            startSession(sourceNeuronId);
            return;
        }
        activateDuringCurrentTick(sourceNeuronId);
    }

    /** Створює сеанс, синхронно обробляє його перший такт і запускає наступну межу за потреби. */
    private void startSession(String sourceNeuronId) {
        session = simulationService.start(neuronService.model(), sourceNeuronId);
        if (session == null) {
            return;
        }
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        finishDelay.stop();
        updateSimulationControlsVisibility();
        processNextStep();
        if (session != null && !session.isFinished()) {
            startTimelineIfNeeded();
        }
        updateSimulationControlsVisibility();
    }

    /** Застосовує ручну активацію одразу та синхронізує її анімацію з межею поточного такту. */
    private void activateDuringCurrentTick(String sourceNeuronId) {
        double remainingMillis = remainingTickDurationMillis();
        SimulationStep step = session.activateManualStartNow(sourceNeuronId);
        if (step == null) {
            return;
        }
        lastAppliedStep = step;
        runtimeState.apply(step);
        stepPresenter.applyImmediateActivation(step, sourceNeuronId, remainingMillis);
        if (paused || modalSuspended) {
            stepPresenter.pauseAnimations();
        }

        if (session.isFinished()) {
            if (timeline != null || finishDelay.getStatus() == Animation.Status.STOPPED) {
                finishSimulation(remainingMillis);
            }
        } else if (finishDelay.getStatus() == Animation.Status.STOPPED) {
            startTimelineIfNeeded();
        }
        updateSimulationControlsVisibility();
    }

    /** Завершує очікування поточного такту: обробляє сигнали на межі або очищує остаточний runtime-стан. */
    private void finishDelayElapsed() {
        finishDelay.stop();
        if (session != null && session.hasPendingWork()) {
            processNextStep();
            if (session != null && !session.isFinished()) {
                startTimelineIfNeeded();
            }
            return;
        }
        runtimeState.clear();
        stepPresenter.clearRuntime();
        lastAppliedStep = null;
        session = null;
        timeline = null;
        stepPresenter.refresh();
        save.run();
        status.accept(localization.text("status.simulation_finished"));
        updateSimulationControlsVisibility();
    }

    /** Перемикає симуляцію між ручною паузою та виконанням. */
    public void pauseOrResume() {
        if (modalSuspended || !hasActiveSimulation()) {
            return;
        }
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    /**
     * Призупиняє симуляцію для модального інтерфейсу, не змінюючи індикатор ручної паузи на панелі інструментів.
     *
     * @return {@code true}, якщо симуляція виконувалася й була призупинена цим викликом.
     */
    public boolean pauseForModal() {
        if (!hasActiveSimulation() || paused || modalSuspended) {
            return false;
        }
        modalSuspended = true;
        pauseInternal(false);
        return true;
    }

    /** Відновлює симуляцію після модального інтерфейсу, не змінюючи індикатор ручної паузи. */
    public void resumeFromModal() {
        if (!modalSuspended) {
            return;
        }
        modalSuspended = false;
        if (!hasActiveSimulation() || paused) {
            return;
        }
        resumeInternal(false);
    }

    /** Повертає, чи симуляція перебуває в ручній паузі. */
    public boolean isPaused() {
        return paused;
    }

    /** Перевіряє наявність активного сеансу, таймера або анімації симуляції. */
    public boolean hasActiveSimulation() {
        return session != null
                || timeline != null
                || finishDelay.getStatus() != Animation.Status.STOPPED
                || stepPresenter.hasActiveAnimations();
    }

    /**
     * Відновлює індикатори останнього обробленого такту після відновлення моделі карти.
     * Не перезапускає наявні анімації та не створює імпульси для зв'язків, яких не було під час цього такту.
     * Сеанс, черги сигналів, часову шкалу й стан паузи залишає без змін.
     */
    public void refreshPresentationAfterMapHistoryChange() {
        if (lastAppliedStep == null) {
            runtimeState.clear();
            stepPresenter.clearRuntime();
            stepPresenter.refresh();
            updateSimulationControlsVisibility();
            return;
        }

        runtimeState.apply(lastAppliedStep);
        stepPresenter.restoreVisualState(lastAppliedStep);
        if (paused || modalSuspended) {
            stepPresenter.pauseAnimations();
        }
        updateSimulationControlsVisibility();
    }

    /** Зупиняє поширення сигналів, очищує тимчасовий стан симуляції та оновлює індикатори без збереження карти. */
    public void stopSignals() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        stepPresenter.refresh();
        updateSimulationControlsVisibility();
        status.accept(localization.text("status.simulation_stopped"));
    }

    /** Перевіряє та застосовує тривалість одного такту симуляції в мілісекундах. */
    public void setTickDurationMillis(double millis) {
        tickMillis = SimulationSpeed.requireMillis(millis, minTickMillis, maxTickMillis);
        stepPresenter.setTickDurationMillis(tickMillis);
        if (timeline == null) {
            finishDelay.setDuration(Duration.millis(tickMillis));
            return;
        }

        boolean shouldRemainPaused = paused || modalSuspended;
        timeline.stop();
        createTimeline();
        if (!shouldRemainPaused) {
            timeline.play();
        }
        finishDelay.setDuration(Duration.millis(tickMillis));
        status.accept(localization.text(
                "status.speed_changed", NumberDisplayFormatter.format(tickMillis)));
    }

    /** Зупиняє симуляцію та очищує пов'язаний активний стан. */
    public void stop() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        stepPresenter.refresh();
        updateSimulationControlsVisibility();
    }

    /** Зупиняє таймери й анімацію та звільняє ресурси симуляції. */
    public void shutdown() {
        resetRuntime();
        paused = false;
        modalSuspended = false;
        notifyPausedState();
        updateSimulationControlsVisibility();
    }

    /** Призупиняє симуляцію вручну й повідомляє користувача про зміну її стану. */
    private void pause() {
        if (!hasActiveSimulation()) {
            return;
        }
        pauseInternal(true);
        status.accept(localization.text("status.simulation_paused"));
    }

    /** Призупиняє таймери симуляції та візуальні імпульси, окремо керуючи ручним індикатором паузи. */
    private void pauseInternal(boolean notifyManualPauseState) {
        if (notifyManualPauseState) {
            paused = true;
        }
        if (timeline != null) {
            timeline.pause();
        }
        if (finishDelay.getStatus() == Animation.Status.RUNNING) {
            finishDelay.pause();
        }
        stepPresenter.pauseAnimations();
        if (notifyManualPauseState) {
            notifyPausedState();
        }
        updateSimulationControlsVisibility();
    }

    /** Відновлює симуляцію після ручної паузи й повідомляє користувача. */
    private void resume() {
        resumeInternal(true);
        status.accept(localization.text("status.simulation_resumed"));
    }

    /** Відновлює таймери симуляції та імпульсні анімації з поточного стану. */
    private void resumeInternal(boolean notifyManualPauseState) {
        if (notifyManualPauseState) {
            paused = false;
        }
        startTimelineIfNeeded();
        if (finishDelay.getStatus() == Animation.Status.PAUSED) {
            finishDelay.play();
        }
        stepPresenter.resumeAnimations();
        if (notifyManualPauseState) {
            notifyPausedState();
        }
        updateSimulationControlsVisibility();
    }

    /**
     * Спочатку завершує імпульси попереднього такту, потім обчислює й показує наступний результат симуляції.
     * Такий порядок не дозволяє незалежним часовим шкалам губити імпульси на спільній межі такту.
     */
    private void processNextStep() {
        if (session == null) {
            return;
        }
        stepPresenter.completeAnimationsAtTickBoundary();
        SimulationStep step = session.nextStep();
        if (step == null) {
            lastAppliedStep = null;
            finishSimulation();
            return;
        }
        lastAppliedStep = step;
        runtimeState.apply(step);
        stepPresenter.apply(step);
        status.accept(localization.text("status.tick", step.tick().add(BigInteger.ONE)));
        if (session.isFinished()) {
            finishSimulation();
        }
    }

    /** Завершує симуляцію після заданого інтервалу, щоб імпульси встигли синхронно дійти до кінця такту. */
    private void finishSimulation() {
        finishSimulation(tickMillis);
    }

    /**
     * Зупиняє активний таймер і запускає відкладене очищення з потрібним залишком поточного такту.
     *
     * @param delayMillis час до завершення поточного такту в мілісекундах.
     */
    private void finishSimulation(double delayMillis) {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        double safeDelay = Double.isFinite(delayMillis) && delayMillis > 0.0
                ? delayMillis
                : MINIMUM_ANIMATION_MILLIS;
        finishDelay.setDuration(Duration.millis(safeDelay));
        finishDelay.playFromStart();
        if (paused || modalSuspended) {
            finishDelay.pause();
        }
        updateSimulationControlsVisibility();
    }

    /**
     * Обчислює залишок поточного такту за активною часовою шкалою або таймером завершення.
     *
     * @return додатний час до найближчої межі такту в мілісекундах.
     */
    private double remainingTickDurationMillis() {
        double duration = tickMillis;
        double elapsed = 0.0;
        if (timeline != null && timeline.getStatus() != Animation.Status.STOPPED) {
            elapsed = timeline.getCurrentTime().toMillis();
        } else if (finishDelay.getStatus() != Animation.Status.STOPPED) {
            duration = finishDelay.getDuration().toMillis();
            elapsed = finishDelay.getCurrentTime().toMillis();
        }
        if (!Double.isFinite(elapsed) || elapsed < 0.0) {
            elapsed = 0.0;
        }
        double remaining = duration - elapsed;
        if (!Double.isFinite(remaining) || remaining < MINIMUM_ANIMATION_MILLIS) {
            return MINIMUM_ANIMATION_MILLIS;
        }
        return remaining;
    }

    /** Скидає поточний сеанс, таймер і тимчасові сигнали симуляції. */
    private void resetRuntime() {
        if (timeline != null) {
            timeline.stop();
        }
        finishDelay.stop();
        runtimeState.clear();
        timeline = null;
        session = null;
        lastAppliedStep = null;
        stepPresenter.clearRuntime();
    }

    /** Створює часову шкалу, яка обробляє наступні такти симуляції. */
    private void createTimeline() {
        timeline = new Timeline(new KeyFrame(Duration.millis(tickMillis), event -> processNextStep()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    /** Запускає часову шкалу лише тоді, коли сеанс має роботу та ще не виконується. */
    private void startTimelineIfNeeded() {
        if (session == null || paused || modalSuspended
                || finishDelay.getStatus() != Animation.Status.STOPPED || !session.hasPendingWork()) {
            return;
        }
        if (timeline == null) {
            createTimeline();
        }
        if (timeline.getStatus() != Animation.Status.RUNNING) {
            timeline.play();
        }
    }

    /** Передає ручний стан паузи зареєстрованому споживачу панелі інструментів. */
    private void notifyPausedState() {
        pausedStateConsumer.accept(paused);
    }

    /** Показує або приховує елементи керування відповідно до активності симуляції. */
    private void updateSimulationControlsVisibility() {
        simulationActivityConsumer.accept(hasActiveSimulation());
    }
}
