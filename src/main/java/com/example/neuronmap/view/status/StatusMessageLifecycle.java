package com.example.neuronmap.view.status;

import com.example.neuronmap.view.support.UiTiming;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

/** Керує часом видимості повідомлення рядка стану, наведенням курсора, призупиненням і згасанням. */
public final class StatusMessageLifecycle {

    private static final Duration VISIBLE_DURATION = UiTiming.STATUS_MESSAGE_VISIBLE_DURATION;
    private static final Duration FADE_DURATION = UiTiming.STATUS_MESSAGE_FADE_DURATION;

    private final Node hoverRegion;
    private final Label messageLabel;
    private final PauseTransition visiblePause;
    private final FadeTransition fadeTransition;

    private boolean hovered;
    private boolean messageFinished;
    private boolean suspended;

    /**
     * Створює життєвий цикл повідомлення для заданої області наведення та текстової мітки.
     *
     * @param hoverRegion вузол рядка стану, який визначає наведення курсора.
     * @param messageLabel мітка, у якій відображається текст повідомлення.
     * @throws IllegalArgumentException якщо будь-яку із залежностей не задано.
     */
    public StatusMessageLifecycle(Node hoverRegion, Label messageLabel) {
        this(hoverRegion, messageLabel, VISIBLE_DURATION, FADE_DURATION);
    }

    /**
     * Створює життєвий цикл із заданими тривалостями для перевірки часової поведінки без очікування повних п'яти секунд.
     * Використовує ті самі переходи, що й робочий конструктор, але дозволяє тестам перевірити збереження прогресу.
     *
     * @param hoverRegion вузол рядка стану, який визначає наведення курсора.
     * @param messageLabel мітка, у якій відображається текст повідомлення.
     * @param visibleDuration тривалість початкового показу повідомлення.
     * @param fadeDuration тривалість плавного приховування повідомлення.
     * @throws IllegalArgumentException якщо будь-який аргумент не задано.
     */
    StatusMessageLifecycle(
            Node hoverRegion,
            Label messageLabel,
            Duration visibleDuration,
            Duration fadeDuration
    ) {
        if (hoverRegion == null) {
            throw new IllegalArgumentException("hoverRegion must not be null");
        }
        if (messageLabel == null) {
            throw new IllegalArgumentException("messageLabel must not be null");
        }
        if (visibleDuration == null) {
            throw new IllegalArgumentException("visibleDuration must not be null");
        }
        if (fadeDuration == null) {
            throw new IllegalArgumentException("fadeDuration must not be null");
        }

        this.hoverRegion = hoverRegion;
        this.messageLabel = messageLabel;
        this.visiblePause = new PauseTransition(visibleDuration);
        this.fadeTransition = new FadeTransition(fadeDuration);
        fadeTransition.setNode(messageLabel);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        fadeTransition.setOnFinished(event -> {
            messageLabel.setOpacity(0.0);
            messageFinished = true;
            hoverRegion.setMouseTransparent(true);
        });
        visiblePause.setOnFinished(event -> {
            if (!hovered && !messageFinished && !suspended) {
                playFade();
            }
        });

        hoverRegion.setOnMouseEntered(event -> handleMouseEntered());
        hoverRegion.setOnMouseExited(event -> handleMouseExited());
        hoverRegion.setMouseTransparent(true);
        messageLabel.setOpacity(0.0);
    }

    /** Показує повідомлення та запускає відлік часу, якщо рядок стану не призупинений. */
    public void show(String message) {
        String normalizedMessage = message == null ? "" : message;
        visiblePause.stop();
        fadeTransition.stop();
        messageFinished = false;
        hovered = !suspended && hoverRegion.isHover();
        messageLabel.setText(normalizedMessage);
        messageLabel.setOpacity(1.0);
        hoverRegion.setMouseTransparent(false);
        restartVisibleCountdown();
    }

    /** Прибирає повідомлення й скасовує таймери, не змінюючи встановлену зовнішнім координатором паузу. */
    public void clear() {
        visiblePause.stop();
        fadeTransition.stop();
        hovered = false;
        messageFinished = true;
        messageLabel.setOpacity(0.0);
        hoverRegion.setMouseTransparent(true);
    }

    /** Призупиняє відлік і згасання повідомлення зі збереженням поточного прогресу анімацій. */
    public void pause() {
        if (suspended) {
            return;
        }
        suspended = true;
        pauseIfRunning(visiblePause);
        pauseIfRunning(fadeTransition);
    }

    /** Відновлює відлік або згасання; новий відлік починається лише тоді, коли курсор не накриває повідомлення. */
    public void resume() {
        if (!suspended) {
            return;
        }
        suspended = false;
        hovered = hoverRegion.isHover();
        if (hovered && !messageFinished) {
            visiblePause.stop();
            fadeTransition.stop();
            messageLabel.setOpacity(1.0);
            return;
        }
        if (fadeTransition.getStatus() == Animation.Status.PAUSED) {
            fadeTransition.play();
            return;
        }
        if (visiblePause.getStatus() == Animation.Status.PAUSED) {
            visiblePause.play();
            return;
        }
        restartVisibleCountdown();
    }

    /** Повертає, чи призупинено часові процеси повідомлення зовнішнім координатором. */
    public boolean isSuspended() {
        return suspended;
    }

    /** Від'єднує обробники подій і звільняє таймери та анімації компонента. */
    public void dispose() {
        clear();
        hoverRegion.setOnMouseEntered(null);
        hoverRegion.setOnMouseExited(null);
        visiblePause.setOnFinished(null);
        fadeTransition.setOnFinished(null);
    }

    /** Зупиняє приховування повідомлення, поки курсор перебуває над його областю. */
    private void handleMouseEntered() {
        if (messageFinished) {
            return;
        }
        hovered = true;
        visiblePause.stop();
        if (fadeTransition.getStatus() == Animation.Status.RUNNING) {
            fadeTransition.stop();
            messageLabel.setOpacity(1.0);
        }
    }

    /** Відновлює відлік після виходу курсора з області повідомлення. */
    private void handleMouseExited() {
        if (messageFinished) {
            return;
        }
        hovered = false;
        messageLabel.setOpacity(1.0);
        restartVisibleCountdown();
    }

    /** Перезапускає повний час видимості лише для активного, не призупиненого повідомлення. */
    private void restartVisibleCountdown() {
        if (messageFinished || suspended || hovered) {
            return;
        }
        visiblePause.playFromStart();
    }

    /** Запускає згасання лише тоді, коли курсор не наведений, а повідомлення не призупинене. */
    private void playFade() {
        if (hovered || messageFinished || suspended) {
            return;
        }
        fadeTransition.playFromStart();
    }

    /** Призупиняє анімацію тільки тоді, коли вона активно виконується. */
    private static void pauseIfRunning(Animation animation) {
        if (animation.getStatus() == Animation.Status.RUNNING) {
            animation.pause();
        }
    }
}
