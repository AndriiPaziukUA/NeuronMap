package com.example.neuronmap.controller;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import com.example.neuronmap.view.simulation.PulseAnimator;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Керує життєвим циклом імпульсів і синхронізує їхню видимість та геометрію зі зв’язками. */
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
        workspace.edgeLayer().getChildren().addListener(
                (ListChangeListener<Node>) _ -> synchronizeConnectionViews());
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
     * Геометрія й видимість активного імпульсу оновлюються за подіями подання зв'язку, без циклу опитування.
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
        Connection connection = connectionView.model();
        if (hasActiveAnimationForConnection(connection.id())) {
            return;
        }

        ConnectionPulseSnapshot snapshot = connectionView.capturePulseSnapshot();
        if (snapshot == null) {
            return;
        }

        PulseAnimationView animation = new PulseAnimationView(
                snapshot,
                connectionView::capturePulseSnapshot,
                durationMillis,
                this::handleAnimationFinished
        );

        workspace.pulseLayer().getChildren().add(animation);
        ActiveAnimation activeAnimation = new ActiveAnimation(
                animation,
                connection.id(),
                connectionView,
                connection.sourceId(),
                connection.targetId()
        );
        activeAnimations.add(activeAnimation);
        attachListeners(activeAnimation, connectionView);
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

    /**
     * Реєструє слухачі геометрії та видимості зв'язку для синхронізації імпульсу без перезапуску Timeline.
     *
     * @param activeAnimation запис анімації, який потрібно синхронізувати.
     * @param connectionView подання зв'язку, за змінами якого слід стежити.
     */
    private void attachListeners(ActiveAnimation activeAnimation, ConnectionView connectionView) {
        ChangeListener<Number> geometryListener =
                (_, _, _) -> activeAnimation.view().refreshGeometry();
        ChangeListener<Boolean> visibilityListener = (_, _, visible) -> {
            activeAnimation.view().setVisible(visible);
            if (visible) {
                activeAnimation.view().refreshGeometry();
            }
        };
        activeAnimation.setGeometryListener(geometryListener);
        activeAnimation.setVisibilityListener(visibilityListener);
        connectionView.geometryRevisionProperty().addListener(geometryListener);
        connectionView.visibleProperty().addListener(visibilityListener);
        activeAnimation.view().setVisible(connectionView.isVisible());
    }

    /**
     * Узгоджує активні імпульси зі зв'язками, які зараз присутні на полотні.
     * Якщо зв'язок тимчасово зник або став невидимим, часову шкалу імпульсу збережено, але сам імпульс приховано.
     * Після повернення видимості геометрія оновлюється з поточним прогресом, а не скидається на початок.
     */
    private void synchronizeConnectionViews() {
        for (ActiveAnimation activeAnimation : List.copyOf(activeAnimations)) {
            ConnectionView replacement = findConnectionView(activeAnimation.connectionId());
            if (replacement != null && !activeAnimation.matchesConnection(replacement.model())) {
                activeAnimation.view().stop();
                continue;
            }

            ConnectionView current = activeAnimation.connectionView();
            if (current == replacement) {
                boolean visible = replacement != null && replacement.isVisible();
                activeAnimation.view().setVisible(visible);
                if (visible) {
                    activeAnimation.view().refreshGeometry();
                }
                continue;
            }

            detachListeners(activeAnimation);
            activeAnimation.setConnectionView(replacement);
            if (replacement == null) {
                activeAnimation.view().setVisible(false);
                activeAnimation.view().setSnapshotSupplier(() -> null);
                continue;
            }

            activeAnimation.view().setSnapshotSupplier(replacement::capturePulseSnapshot);
            attachListeners(activeAnimation, replacement);
            activeAnimation.view().refreshGeometry();
        }
        pruneFinished();
    }

    /** Знаходить поточне графічне подання зв'язку за стабільним ідентифікатором. */
    private ConnectionView findConnectionView(String connectionId) {
        for (Node node : workspace.edgeLayer().getChildren()) {
            if (node instanceof ConnectionView connectionView
                    && connectionView.model().id().equals(connectionId)) {
                return connectionView;
            }
        }
        return null;
    }

    /** Від'єднує слухачі геометрії та видимості від поточного подання зв'язку, якщо воно існує. */
    private void detachListeners(ActiveAnimation activeAnimation) {
        ConnectionView connectionView = activeAnimation.connectionView();
        ChangeListener<Number> geometryListener = activeAnimation.geometryListener();
        ChangeListener<Boolean> visibilityListener = activeAnimation.visibilityListener();
        if (connectionView != null) {
            if (geometryListener != null) {
                connectionView.geometryRevisionProperty().removeListener(geometryListener);
            }
            if (visibilityListener != null) {
                connectionView.visibleProperty().removeListener(visibilityListener);
            }
        }
        activeAnimation.setGeometryListener(null);
        activeAnimation.setVisibilityListener(null);
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
            detachListeners(activeAnimation);
            return true;
        });
    }

    /** Зберігає посилання на анімацію та її кінці, щоб узгоджувати відновлення відповідного зв'язку. */
    private static final class ActiveAnimation {
        private final PulseAnimationView view;
        private final String connectionId;
        private final String sourceNeuronId;
        private final String targetNeuronId;
        private ConnectionView connectionView;
        private ChangeListener<Number> geometryListener;
        private ChangeListener<Boolean> visibilityListener;

        /**
         * Створює запис керування імпульсом і запам'ятовує стабільну ідентичність зв'язку.
         *
         * @param view графічна анімація імпульсу.
         * @param connectionId незмінний ідентифікатор напрямленого зв'язку.
         * @param connectionView поточне графічне подання зв'язку.
         * @param sourceNeuronId ідентифікатор нейрона-джерела.
         * @param targetNeuronId ідентифікатор нейрона-цілі.
         */
        private ActiveAnimation(
                PulseAnimationView view,
                String connectionId,
                ConnectionView connectionView,
                String sourceNeuronId,
                String targetNeuronId
        ) {
            this.view = view;
            this.connectionId = connectionId;
            this.connectionView = connectionView;
            this.sourceNeuronId = sourceNeuronId;
            this.targetNeuronId = targetNeuronId;
        }

        /** Повертає кероване графічне подання імпульсу. */
        private PulseAnimationView view() {
            return view;
        }

        /** Повертає стабільний ідентифікатор напрямленого зв'язку. */
        private String connectionId() {
            return connectionId;
        }

        /** Повертає поточне подання зв'язку або {@code null}, якщо зв'язок тимчасово відсутній. */
        private ConnectionView connectionView() {
            return connectionView;
        }

        /** Замінює подання зв'язку, до якого прив'язана анімація. */
        private void setConnectionView(ConnectionView connectionView) {
            this.connectionView = connectionView;
        }

        /** Зберігає слухач актуалізації імпульсу після зміни геометрії зв'язку. */
        private void setGeometryListener(ChangeListener<Number> geometryListener) {
            this.geometryListener = geometryListener;
        }

        /** Повертає слухач геометрії для від'єднання після завершення анімації. */
        private ChangeListener<Number> geometryListener() {
            return geometryListener;
        }

        /** Зберігає слухач, що синхронізує видимість імпульсу зі зв'язком. */
        private void setVisibilityListener(ChangeListener<Boolean> visibilityListener) {
            this.visibilityListener = visibilityListener;
        }

        /** Повертає слухач видимості для коректного звільнення прив'язки до JavaFX-властивості. */
        private ChangeListener<Boolean> visibilityListener() {
            return visibilityListener;
        }

        /** Перевіряє, чи належить анімація вказаному напрямленому зв'язку. */
        private boolean isForConnection(String candidateConnectionId) {
            return connectionId.equals(candidateConnectionId);
        }

        /** Перевіряє відповідність зв'язку початковому ID та початковим і кінцевим нейронам. */
        private boolean matchesConnection(Connection connection) {
            return connectionId.equals(connection.id())
                    && sourceNeuronId.equals(connection.sourceId())
                    && targetNeuronId.equals(connection.targetId());
        }

        /** Перевіряє, чи проходить зв'язок через нейрон, який зараз видаляють. */
        private boolean isRelatedTo(String neuronId) {
            return sourceNeuronId.equals(neuronId) || targetNeuronId.equals(neuronId);
        }
    }
}
