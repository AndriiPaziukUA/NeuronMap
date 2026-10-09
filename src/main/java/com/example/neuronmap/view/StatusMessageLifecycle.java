package com.example.neuronmap.view;

import com.example.neuronmap.util.UiTiming;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Керує життєвим циклом повідомлення рядка стану: часом видимості, реакцією на наведення й плавним зникненням.
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
     * Створює екземпляр StatusMessageLifecycle та зберігає передані залежності, потрібні для його роботи.
     *
     * @param hoverRegion значення «hover region», яке використовується в цьому методі.
     * @param messageLabel значення «message label», яке використовується в цьому методі.
     */
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

    /**
     * Показує повідомлення та запускає таймер його видимості.
     *
     * @param message значення «message», яке використовується в цьому методі.
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
     * Прибирає текст повідомлення й скасовує таймери його приховування.
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
     * Від’єднує обробники подій і звільняє ресурси, якими керує компонент.
     */
    public void dispose() {
        clear();
        hoverRegion.setOnMouseEntered(null);
        hoverRegion.setOnMouseExited(null);
        visiblePause.setOnFinished(null);
        fadeTransition.setOnFinished(null);
    }

    /**
     * Зупиняє приховування повідомлення, поки курсор перебуває над ним.
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
     * Відновлює відлік часу приховування після виходу курсора.
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
     * Перезапускає таймер видимості повідомлення після наведення курсора.
     */
    private void restartVisibleCountdown() {
        if (messageFinished) {
            return;
        }

        visiblePause.playFromStart();
    }

    /**
     * Запускає плавне зникнення повідомлення після завершення часу видимості.
     */
    private void playFade() {
        if (hovered || messageFinished) {
            return;
        }

        fadeTransition.playFromStart();
    }
}
