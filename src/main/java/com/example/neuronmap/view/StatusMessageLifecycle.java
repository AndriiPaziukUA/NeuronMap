package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Керує строком показу повідомлення стану та його завершенням.
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

    /**
     * Повертає результат операції «стан повідомлення».
     *
     * @param hoverRegion значення, що визначає наведення для цієї операції.
     *
     * @param messageLabel значення, що визначає повідомлення підпис для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StatusMessageLifecycle(
            Node hoverRegion,
            Label messageLabel
    ) {
        if (hoverRegion == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "hoverRegion must not be null"
            );
        }
        if (messageLabel == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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

    /**
     * Відображає «потрібні дані» в інтерфейсі.
     *
     * @param message повідомлення для показу чи журналювання.
     */
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

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     */
    public void clear() {
        visiblePause.stop();
        fadeTransition.stop();
        hovered = false;
        messageFinished = true;
        messageLabel.setOpacity(0.0);
        hoverRegion.setMouseTransparent(true);
    }

    /**
     * Завершує або скасовує дію, повʼязану з «потрібні дані».
     */
    public void dispose() {
        clear();
        hoverRegion.setOnMouseEntered(null);
        hoverRegion.setOnMouseExited(null);
        visiblePause.setOnFinished(null);
        fadeTransition.setOnFinished(null);
    }

    /**
     * Обробляє «відповідну операцію».
     */
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

    /**
     * Обробляє «відповідну операцію».
     */
    private void handleMouseExited() {
        if (messageFinished) {
            return;
        }

        hovered = false;
        messageLabel.setOpacity(1.0);
        restartVisibleCountdown();
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void restartVisibleCountdown() {
        if (messageFinished) {
            return;
        }

        visiblePause.playFromStart();
    }

    /**
     * Виконує операцію «відповідну операцію».
     */
    private void playFade() {
        if (hovered || messageFinished) {
            return;
        }

        fadeTransition.playFromStart();
    }
}
