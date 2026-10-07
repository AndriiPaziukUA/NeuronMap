package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Controls delayed disappearance and hover-to-restore behavior of one status
 * message area.
 */
public final class StatusMessageLifecycle {

    private static final Duration VISIBLE_DURATION =
            UiTiming.STATUS_MESSAGE_VISIBLE_DURATION;
    private static final Duration FADE_DURATION =
            UiTiming.STATUS_MESSAGE_FADE_DURATION;

    private final Node hoverRegion;
    private final Label messageLabel;
    private final PauseTransition visiblePause =
            new PauseTransition(VISIBLE_DURATION);
    private final FadeTransition fadeTransition =
            new FadeTransition(FADE_DURATION);

    private boolean hovered;
    private boolean messageFinished;

    public StatusMessageLifecycle(
            Node hoverRegion,
            Label messageLabel
    ) {
        if (hoverRegion == null) {
            throw new IllegalArgumentException(
                    "hoverRegion must not be null"
            );
        }
        if (messageLabel == null) {
            throw new IllegalArgumentException(
                    "messageLabel must not be null"
            );
        }

        this.hoverRegion = hoverRegion;
        this.messageLabel = messageLabel;

        fadeTransition.setNode(messageLabel);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        fadeTransition.setOnFinished(
                event -> {
                    messageLabel.setOpacity(0.0);
                    messageFinished = true;
                    hoverRegion.setMouseTransparent(true);
                }
        );

        visiblePause.setOnFinished(
                event -> {
                    if (!hovered && !messageFinished) {
                        playFade();
                    }
                }
        );

        hoverRegion.setOnMouseEntered(
                event -> handleMouseEntered()
        );
        hoverRegion.setOnMouseExited(
                event -> handleMouseExited()
        );

        hoverRegion.setMouseTransparent(true);
        messageLabel.setOpacity(0.0);
    }

    public void show(String message) {
        String normalizedMessage =
                message == null ? "" : message;

        visiblePause.stop();
        fadeTransition.stop();

        messageFinished = false;
        hovered = hoverRegion.isHover();
        messageLabel.setText(normalizedMessage);
        messageLabel.setOpacity(1.0);
        hoverRegion.setMouseTransparent(false);

        restartVisibleCountdown();
    }

    public void clear() {
        visiblePause.stop();
        fadeTransition.stop();
        hovered = false;
        messageFinished = true;
        messageLabel.setOpacity(0.0);
        hoverRegion.setMouseTransparent(true);
    }

    public void dispose() {
        clear();
        hoverRegion.setOnMouseEntered(null);
        hoverRegion.setOnMouseExited(null);
        visiblePause.setOnFinished(null);
        fadeTransition.setOnFinished(null);
    }

    private void handleMouseEntered() {
        if (messageFinished) {
            return;
        }

        hovered = true;
        visiblePause.stop();

        if (fadeTransition.getStatus()
                == javafx.animation.Animation.Status.RUNNING) {
            fadeTransition.stop();
            messageLabel.setOpacity(1.0);
        }
    }

    private void handleMouseExited() {
        if (messageFinished) {
            return;
        }

        hovered = false;
        messageLabel.setOpacity(1.0);
        restartVisibleCountdown();
    }

    private void restartVisibleCountdown() {
        if (messageFinished) {
            return;
        }

        visiblePause.playFromStart();
    }

    private void playFade() {
        if (hovered || messageFinished) {
            return;
        }

        fadeTransition.playFromStart();
    }
}
