package com.example.neuronmap.controller;

import com.example.neuronmap.view.ConnectionPulseSnapshot;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.PulseAnimationView;
import com.example.neuronmap.view.WorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Керує запуском, призупиненням, відновленням і зупиненням анімацій імпульсів на зв’язках.
 */
public final class PulseAnimationController {

    private final WorkspaceView workspace;
    private final Runnable activityChanged;
    private final List<PulseAnimationView> activeAnimations = new ArrayList<>();

    /**
     * Створює екземпляр PulseAnimationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param workspace полотно редактора.
     */
    public PulseAnimationController(WorkspaceView workspace) {
        this(workspace, () -> { });
    }

    /**
     * Створює екземпляр PulseAnimationController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param workspace полотно редактора.
     * @param activityChanged callback, який повідомляє про зміну активності анімацій.
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
     * Призупиняє «all», зберігаючи можливість подальшого відновлення.
     */
    public void pauseAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.pause();
        }
    }

    /**
     * Відновлює «all» після призупинення.
     */
    public void resumeAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.resume();
        }
    }

    /**
     * Зупиняє all та очищає пов’язаний активний стан.
     */
    public void stopAll() {
        for (PulseAnimationView animation : List.copyOf(activeAnimations)) {
            animation.stop();
        }
        activeAnimations.clear();
        activityChanged.run();
    }

    /**
     * Перевіряє, чи є active animations у поточному стані.
     *
     * @return {@code true}, якщо умову виконано; інакше {@code false}.
     */
    public boolean hasActiveAnimations() {
        pruneFinished();
        return !activeAnimations.isEmpty();
    }

    /**
     * Надає тестам доступ до «active animation count» для перевірки стану інтерфейсу.
     */
    int activeAnimationCountForTest() {
        pruneFinished();
        return activeAnimations.size();
    }

    /**
     * Обробляє подію «animation finished» і передає її до відповідної операції редактора.
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
