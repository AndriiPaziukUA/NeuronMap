package com.example.neuronmap.controller;

import com.example.neuronmap.view.ConnectionPulseSnapshot;
import com.example.neuronmap.view.ConnectionView;
import com.example.neuronmap.view.PulseAnimationView;
import com.example.neuronmap.view.WorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns detached signal visuals so they survive graph element deletion. */
public final class PulseAnimationController {

    private final WorkspaceView workspace;
    private final Runnable activityChanged;
    private final List<PulseAnimationView> activeAnimations = new ArrayList<>();

    public PulseAnimationController(WorkspaceView workspace) {
        this(workspace, () -> { });
    }

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
                        this::handleAnimationFinished
                );

        workspace.pulseLayer().getChildren().add(animation);
        activeAnimations.add(animation);
        animation.play();
        pruneFinished();
        activityChanged.run();
    }

    public void pauseAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.pause();
        }
    }

    public void resumeAll() {
        pruneFinished();
        for (PulseAnimationView animation : activeAnimations) {
            animation.resume();
        }
    }

    public void stopAll() {
        for (PulseAnimationView animation : List.copyOf(activeAnimations)) {
            animation.stop();
        }
        activeAnimations.clear();
        activityChanged.run();
    }

    public boolean hasActiveAnimations() {
        pruneFinished();
        return !activeAnimations.isEmpty();
    }

    int activeAnimationCountForTest() {
        pruneFinished();
        return activeAnimations.size();
    }

    private void handleAnimationFinished() {
        pruneFinished();
        activityChanged.run();
    }

    private void pruneFinished() {
        activeAnimations.removeIf(PulseAnimationView::isFinished);
    }
}
