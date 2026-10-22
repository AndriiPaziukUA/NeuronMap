package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import com.example.neuronmap.view.simulation.PulseAnimator;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Керує запуском, призупиненням, відновленням і зупиненням анімацій імпульсів. */
public final class PulseAnimationController implements PulseAnimator {

    private final WorkspaceView workspace;
    private final Runnable activityChanged;
    private final List<ActiveAnimation> activeAnimations = new ArrayList<>();
    private double tickDurationMillis = PulseAnimationView.DEFAULT_TICK_DURATION_MILLIS;

    /**
     * Створює контролер анімацій імпульсів.
     *
     * @param workspace полотно редактора.
     */
    public PulseAnimationController(WorkspaceView workspace) {
        this(workspace, () -> { });
    }

    /**
     * Створює контролер анімацій імпульсів із callback-ом зміни активності.
     *
     * @param workspace полотно редактора.
     * @param activityChanged дія, що виконується після зміни активності анімацій.
     */
    public PulseAnimationController(
            WorkspaceView workspace,
            Runnable activityChanged
    ) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.activityChanged = Objects.requireNonNull(activityChanged, "activityChanged");
    }

    /**
     * Створює та запускає анімацію імпульсу для заданого подання зв'язку зі стандартною тривалістю такту.
     *
     * @param connectionView візуальне подання зв'язку.
     */
    @Override
    public void play(ConnectionView connectionView) {
        play(connectionView, tickDurationMillis);
    }

    /**
     * Створює та запускає анімацію імпульсу з індивідуальною тривалістю, не змінюючи налаштування інших анімацій.
     * Геометрія активного імпульсу оновлюється через подію зв'язку, а не через окремий цикл кадрів.
     *
     * @param connectionView візуальне подання зв'язку.
     * @param durationMillis тривалість саме цієї анімації в мілісекундах.
     * @throws IllegalArgumentException якщо тривалість не є скінченним додатним числом.
     */
    @Override
    public void play(ConnectionView connectionView, double durationMillis) {
        if (connectionView == null) {
            return;
        }
        if (!Double.isFinite(durationMillis) || durationMillis <= 0.0) {
            throw new IllegalArgumentException("animation duration must be finite and positive");
        }

        pruneFinished();
        String connectionId = connectionView.model().id();
        if (hasActiveAnimationForConnection(connectionId)) {
            return;
        }

        ConnectionPulseSnapshot snapshot = connectionView.capturePulseSnapshot();
        if (snapshot == null) {
            return;
        }

        Connection connection = connectionView.model();
        PulseAnimationView animation = new PulseAnimationView(
                snapshot,
                connectionView::capturePulseSnapshot,
                durationMillis,
                this::handleAnimationFinished
        );

        workspace.pulseLayer().getChildren().add(animation);
        ActiveAnimation activeAnimation = new ActiveAnimation(
                animation,
                connectionView,
                connection.sourceId(),
                connection.targetId()
        );
        ChangeListener<Parent> parentListener = (observable, oldParent, newParent) -> {
            if (oldParent != null && newParent == null) {
                animation.stop();
            }
        };
        ChangeListener<Number> geometryListener = (observable, oldValue, newValue) -> animation.refreshGeometry();
        activeAnimation.setParentListener(parentListener);
        activeAnimation.setGeometryListener(geometryListener);
        connectionView.parentProperty().addListener(parentListener);
        connectionView.geometryRevisionProperty().addListener(geometryListener);
        activeAnimations.add(activeAnimation);
        animation.play();
        pruneFinished();
        activityChanged.run();
    }

    /** Призупиняє всі активні анімації імпульсів. */
    @Override
    public void pauseAll() {
        pruneFinished();
        for (ActiveAnimation activeAnimation : activeAnimations) {
            activeAnimation.view().pause();
        }
    }

    /** Відновлює всі призупинені анімації імпульсів. */
    @Override
    public void resumeAll() {
        pruneFinished();
        for (ActiveAnimation activeAnimation : activeAnimations) {
            activeAnimation.view().resume();
        }
    }

    /** Зупиняє всі активні анімації імпульсів і очищає їхнє відображення. */
    @Override
    public void stopAll() {
        for (ActiveAnimation activeAnimation : List.copyOf(activeAnimations)) {
            activeAnimation.view().stop();
        }
        activeAnimations.clear();
        activityChanged.run();
    }

    /**
     * Негайно прибирає лише ті імпульси, що належать зв'язкам зазначеного нейрона.
     *
     * @param neuronId ідентифікатор нейрона, пов'язані з яким анімації слід зупинити.
     */
    @Override
    public void stopForNeuron(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) {
            return;
        }

        pruneFinished();
        for (ActiveAnimation activeAnimation : List.copyOf(activeAnimations)) {
            if (activeAnimation.isRelatedTo(neuronId)) {
                activeAnimation.view().stop();
            }
        }
    }

    /**
     * Завершує поточні імпульси в кінцевих точках і звільняє зв'язки до запуску сигналів наступного такту.
     * Імпульси природно завершуються самі, але порядок JavaFX callback-ів не гарантує, що це станеться
     * раніше за таймер симуляції; цей метод усуває таку гонку детерміновано.
     */
    @Override
    public void completeAtTickBoundary() {
        for (ActiveAnimation activeAnimation : List.copyOf(activeAnimations)) {
            activeAnimation.view().completeAtTickBoundary();
        }
        pruneFinished();
    }

    /**
     * Застосовує нову тривалість такту до всіх активних імпульсів і майбутніх анімацій.
     * Поточний прогрес активних імпульсів зберігається.
     *
     * @param millis тривалість одного такту в мілісекундах.
     * @throws IllegalArgumentException якщо тривалість не є скінченним додатним числом.
     */
    @Override
    public void setTickDurationMillis(double millis) {
        if (!Double.isFinite(millis) || millis <= 0.0) {
            throw new IllegalArgumentException("tick duration must be finite and positive");
        }
        if (Double.compare(tickDurationMillis, millis) == 0) {
            return;
        }

        tickDurationMillis = millis;
        pruneFinished();
        for (ActiveAnimation activeAnimation : List.copyOf(activeAnimations)) {
            activeAnimation.view().setTickDurationMillis(millis);
        }
    }

    /**
     * Перевіряє, чи є анімації, які ще виконуються.
     *
     * @return {@code true}, якщо є активні анімації; інакше {@code false}.
     */
    @Override
    public boolean hasActiveAnimations() {
        pruneFinished();
        return !activeAnimations.isEmpty();
    }

    /**
     * Надає тестам кількість анімацій, які ще виконуються.
     *
     * @return кількість активних анімацій.
     */
    int activeAnimationCountForTest() {
        pruneFinished();
        return activeAnimations.size();
    }

    /** Перевіряє, чи вже є активний імпульс для заданого напрямленого зв'язку. */
    private boolean hasActiveAnimationForConnection(String connectionId) {
        return activeAnimations.stream().anyMatch(animation -> animation.isForConnection(connectionId));
    }

    /** Обробляє завершення анімації та повідомляє про зміну активності. */
    private void handleAnimationFinished() {
        pruneFinished();
        activityChanged.run();
    }

    /** Видаляє зі списку керування завершені анімації та від'єднує їхні слухачі. */
    private void pruneFinished() {
        activeAnimations.removeIf(activeAnimation -> {
            if (!activeAnimation.view().isFinished()) {
                return false;
            }
            activeAnimation.connectionView().parentProperty().removeListener(activeAnimation.parentListener());
            activeAnimation.connectionView().geometryRevisionProperty()
                    .removeListener(activeAnimation.geometryListener());
            return true;
        });
    }

    /** Зберігає посилання на анімацію та її подання зв'язку для узгодженого керування життєвим циклом. */
    private static final class ActiveAnimation {
        private final PulseAnimationView view;
        private final ConnectionView connectionView;
        private final String sourceNeuronId;
        private final String targetNeuronId;
        private ChangeListener<Parent> parentListener;
        private ChangeListener<Number> geometryListener;

        /** Зберігає анімацію та кінці зв'язку, щоб зупиняти її після видалення відповідного нейрона. */
        private ActiveAnimation(
                PulseAnimationView view,
                ConnectionView connectionView,
                String sourceNeuronId,
                String targetNeuronId
        ) {
            this.view = view;
            this.connectionView = connectionView;
            this.sourceNeuronId = sourceNeuronId;
            this.targetNeuronId = targetNeuronId;
        }

        /** Повертає кероване графічне подання імпульсу. */
        private PulseAnimationView view() {
            return view;
        }

        /** Повертає подання зв'язку, від якого залежить анімація. */
        private ConnectionView connectionView() {
            return connectionView;
        }

        /** Зберігає слухач видалення подання зв'язку для контрольованого життєвого циклу. */
        private void setParentListener(ChangeListener<Parent> parentListener) {
            this.parentListener = parentListener;
        }

        /** Повертає слухач, який потрібно від'єднати після завершення анімації. */
        private ChangeListener<Parent> parentListener() {
            return parentListener;
        }

        /** Зберігає слухач актуалізації імпульсу після зміни геометрії зв'язку. */
        private void setGeometryListener(ChangeListener<Number> geometryListener) {
            this.geometryListener = geometryListener;
        }

        /** Повертає слухач геометрії для від'єднання після завершення анімації. */
        private ChangeListener<Number> geometryListener() {
            return geometryListener;
        }

        /** Перевіряє, чи належить анімація вказаному напрямленому зв'язку. */
        private boolean isForConnection(String connectionId) {
            return connectionView.model().id().equals(connectionId);
        }

        /** Перевіряє, чи проходить зв'язок через нейрон, який зараз видаляють. */
        private boolean isRelatedTo(String neuronId) {
            return sourceNeuronId.equals(neuronId) || targetNeuronId.equals(neuronId);
        }
    }
}
