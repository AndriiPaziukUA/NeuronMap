package com.example.neuronmap.controller;

import com.example.neuronmap.view.connection.ConnectionPulseSnapshot;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.simulation.PulseAnimationView;
import com.example.neuronmap.view.simulation.PulseAnimator;
import com.example.neuronmap.view.editor.WorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Керує запуском, призупиненням, відновленням і зупиненням анімацій імпульсів на зв’язках.
 */
public final class PulseAnimationController implements PulseAnimator {

    private final WorkspaceView workspace;
    private final Runnable activityChanged;
    private final List<PulseAnimationView> activeAnimations = new ArrayList<>();

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
        this.activityChanged = Objects.requireNonNull(
                activityChanged,
                "activityChanged"
        );
    }

    /**
     * Створює та запускає анімацію імпульсу для заданого подання зв’язку.
     *
     * @param connectionView візуальне подання зв’язку.
     */
    @Override
    public void play(ConnectionView connectionView) {
        if (connectionView == null) {
            return;
        }

        ConnectionPulseSnapshot snapshot = connectionView.capturePulseSnapshot();
        if (snapshot == null) {
            return;
        }

        PulseAnimationView animation = new PulseAnimationView(
                snapshot,
                connectionView::capturePulseSnapshot,
                this::handleAnimationFinished
        );

        workspace.pulseLayer().getChildren().add(animation);
        activeAnimations.add(animation);
        animation.play();
        pruneFinished();
        activityChanged.run();
    }

    /**
     * Призупиняє всі активні анімації імпульсів.
     */
    @Override
    public void pauseAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.pause();
        }
    }

    /**
     * Відновлює всі призупинені анімації імпульсів.
     */
    @Override
    public void resumeAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.resume();
        }
    }

    /**
     * Зупиняє всі активні анімації імпульсів і очищає їхнє відображення.
     */
    @Override
    public void stopAll() {
        for (PulseAnimationView animation : List.copyOf(activeAnimations)) {
            animation.stop();
        }
        activeAnimations.clear();
        activityChanged.run();
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

    /**
     * Обробляє завершення анімації та повідомляє про зміну активності.
     */
    private void handleAnimationFinished() {
        pruneFinished();
        activityChanged.run();
    }

    /**
     * Видаляє зі списку керування анімації, які вже завершилися.
     */
    private void pruneFinished() {
        activeAnimations.removeIf(PulseAnimationView::isFinished);
    }
}
