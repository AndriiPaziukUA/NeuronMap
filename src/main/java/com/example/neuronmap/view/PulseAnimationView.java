package com.example.neuronmap.view;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.Objects;

/** Detached visual pulse that does not belong to a ConnectionView lifecycle. */
public final class PulseAnimationView extends Pane {

    private static final double TOTAL_MILLIS = 380.0;
    private static final double ARROW_SIZE = 11.0;
    private static final double ARROW_HALF_WIDTH = 5.5;

    private final Line line = new Line();
    private final Polygon arrow = new Polygon();
    private final Timeline timeline;
    private final Runnable onFinished;
    private boolean finished;

    public PulseAnimationView(ConnectionPulseSnapshot snapshot) {
        this(snapshot, () -> { });
    }

    public PulseAnimationView(
            ConnectionPulseSnapshot snapshot,
            Runnable onFinished
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        this.onFinished = Objects.requireNonNull(
                onFinished,
                "onFinished"
        );

        setManaged(false);
        setPickOnBounds(false);
        setMouseTransparent(true);

        line.setStartX(snapshot.start().getX());
        line.setStartY(snapshot.start().getY());
        line.setEndX(snapshot.end().getX());
        line.setEndY(snapshot.end().getY());
        line.setStroke(snapshot.pulseColor());
        line.setStrokeWidth(2.3);
        line.setMouseTransparent(true);

        Point2D direction = snapshot.end().subtract(snapshot.start());
        if (direction.magnitude() > 0.0) {
            Point2D normalized = direction.normalize();
            Point2D perpendicular = new Point2D(
                    -normalized.getY(),
                    normalized.getX()
            );
            Point2D tip = snapshot.end();
            Point2D back = tip.subtract(
                    normalized.multiply(ARROW_SIZE)
            );
            Point2D left = back.add(
                    perpendicular.multiply(ARROW_HALF_WIDTH)
            );
            Point2D right = back.subtract(
                    perpendicular.multiply(ARROW_HALF_WIDTH)
            );

            arrow.getPoints().setAll(
                    tip.getX(), tip.getY(),
                    left.getX(), left.getY(),
                    right.getX(), right.getY()
            );
        }

        arrow.setFill(snapshot.pulseColor());
        arrow.setMouseTransparent(true);

        getChildren().addAll(line, arrow);

        timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(line.strokeProperty(), snapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), snapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), 1.0),
                        new KeyValue(arrow.opacityProperty(), 1.0)
                ),
                new KeyFrame(
                        Duration.millis(180.0),
                        new KeyValue(line.strokeProperty(), snapshot.pulseColor()),
                        new KeyValue(arrow.fillProperty(), snapshot.pulseColor()),
                        new KeyValue(line.opacityProperty(), 0.2),
                        new KeyValue(arrow.opacityProperty(), 0.2)
                ),
                new KeyFrame(
                        Duration.millis(TOTAL_MILLIS),
                        new KeyValue(line.strokeProperty(), snapshot.normalColor()),
                        new KeyValue(arrow.fillProperty(), snapshot.normalColor()),
                        new KeyValue(line.opacityProperty(), 1.0),
                        new KeyValue(arrow.opacityProperty(), 1.0)
                )
        );

        timeline.setOnFinished(event -> finish());
    }

    public void play() {
        if (!finished) {
            timeline.play();
        }
    }

    public void pause() {
        if (!finished) {
            timeline.pause();
        }
    }

    public void resume() {
        if (!finished) {
            timeline.play();
        }
    }

    public void stop() {
        if (finished) {
            return;
        }
        timeline.stop();
        finish();
    }

    public boolean isFinished() {
        return finished;
    }

    private void finish() {
        if (finished) {
            return;
        }

        finished = true;
        Pane parent = getParent() instanceof Pane pane ? pane : null;
        if (parent != null) {
            parent.getChildren().remove(this);
        }

        onFinished.run();
    }
}

