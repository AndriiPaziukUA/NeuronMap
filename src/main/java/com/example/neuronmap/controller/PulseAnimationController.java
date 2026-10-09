package com.example.neuronmap.controller;

import com.example.neuronmap.view.ConnectionPulseSnapshot;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.PulseAnimationView;
import com.example.neuronmap.view.WorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Керує запуском, оновленням і завершенням анімацій сигналів.
 */
public final class PulseAnimationController {

    private final WorkspaceView workspace;
    private final Runnable activityChanged;
    private final List<PulseAnimationView> activeAnimations = new ArrayList<>();

    /**
     * Повертає результат операції «імпульс анімація».
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PulseAnimationController(WorkspaceView workspace) {
        this(workspace, () -> { });
    }

    /**
     * Повертає результат операції «імпульс анімація».
     *
     * @param workspace значення, що визначає відповідну операцію для цієї операції.
     *
     * @param activityChanged значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
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
     * Виконує операцію «відповідну операцію».
     *
     * @param connectionView значення, що визначає звʼязок відображення для цієї операції.
     */
    public void play(ConnectionView connectionView) {
        if (connectionView == null) {
            return;
        }

        ConnectionPulseSnapshot snapshot = connectionView.capturePulseSnapshot();
        if (snapshot == null) {
            return;
        }

        PulseAnimationView animation =
                new PulseAnimationView(
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
     * Виконує операцію «усі».
     */
    public void pauseAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.pause();
        }
    }

    /**
     * Виконує операцію «усі».
     */
    public void resumeAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.resume();
        }
    }

    /**
     * Завершує або скасовує дію, повʼязану з «усі».
     */
    public void stopAll() {
        for (PulseAnimationView animation : List.copyOf(activeAnimations)) {
            animation.stop();
        }
        activeAnimations.clear();
        activityChanged.run();
    }

    /**
     * Перевіряє, чи виконується умова «анімації».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasActiveAnimations() {
        pruneFinished();
        return !activeAnimations.isEmpty();
    }

    /**
     * Повертає результат операції «анімація для».
     *
     * @return числове значення, визначене методом.
     */
    int activeAnimationCountForTest() {
        pruneFinished();
        return activeAnimations.size();
    }

    /**
     * Обробляє «анімація завершений».
     */
    private void handleAnimationFinished() {
        pruneFinished();
        activityChanged.run();
    }

    /**
     * Виконує операцію «завершений».
     */
    private void pruneFinished() {
        activeAnimations.removeIf(PulseAnimationView::isFinished);
    }
}
