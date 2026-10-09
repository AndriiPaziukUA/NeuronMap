package com.example.neuronmap.view;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/**
 * Збирає шари полотна карти й застосовує світове перетворення до координатної сітки, нейронів, зв’язків та накладок.
 */
public final class WorkspaceView {

    private final StackPane viewport = new StackPane();
    private final Canvas gridCanvas = new Canvas();
    private final GridRenderer gridRenderer =
            new GridRenderer(gridCanvas);

    private final Pane world = new Pane();
    private final Pane groupLayer = new Pane();
    private final Pane edgeLayer = new Pane();
    private final Pane pulseLayer = new Pane();
    private final Pane nodeLayer = new Pane();
    private final Pane overlayLayer = new Pane();

    private final Rectangle clip = new Rectangle();
    private final Scale worldScale = new Scale(
            1.0,
            1.0,
            0.0,
            0.0
    );

    public WorkspaceView() {
        viewport.getStyleClass().add("canvas");
        viewport.setPickOnBounds(true);

        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);

        gridCanvas.setManaged(false);
        gridCanvas.setMouseTransparent(true);
        gridCanvas.setPickOnBounds(false);

        world.setManaged(false);
        world.setPickOnBounds(false);
        world.getTransforms().add(worldScale);

        groupLayer.setManaged(false);
        edgeLayer.setManaged(false);
        pulseLayer.setManaged(false);
        nodeLayer.setManaged(false);

        groupLayer.setMouseTransparent(true);
        pulseLayer.setMouseTransparent(true);
        pulseLayer.setPickOnBounds(false);

        overlayLayer.setManaged(false);
        overlayLayer.setPickOnBounds(false);

        world.getChildren().addAll(
                groupLayer,
                edgeLayer,
                pulseLayer,
                nodeLayer
        );

        viewport.getChildren().addAll(
                gridCanvas,
                world,
                overlayLayer
        );

        StackPane.setAlignment(gridCanvas, Pos.TOP_LEFT);
        StackPane.setAlignment(world, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayLayer, Pos.TOP_LEFT);

        viewport.widthProperty().addListener(
                (obs, oldValue, newValue) -> redrawGrid()
        );
        viewport.heightProperty().addListener(
                (obs, oldValue, newValue) -> redrawGrid()
        );
    }

    /**
     * Повертає кореневий вузол інтерфейсу.
     *
     * @return кореневий вузол інтерфейсу.
     */
    public StackPane node() {
        return viewport;
    }

    /**
     * Повертає шар полотна, на якому відображаються області груп нейронів.
     *
     * @return шар полотна, на якому відображаються області груп нейронів.
     */
    public Pane groupLayer() {
        return groupLayer;
    }

    /**
     * Повертає шар полотна, на якому відображаються зв’язки між нейронами.
     *
     * @return шар полотна, на якому відображаються зв’язки між нейронами.
     */
    public Pane edgeLayer() {
        return edgeLayer;
    }

    /**
     * Повертає шар полотна для анімаційних імпульсів на зв’язках.
     *
     * @return шар полотна для анімаційних імпульсів на зв’язках.
     */
    public Pane pulseLayer() {
        return pulseLayer;
    }

    /**
     * Повертає шар полотна, на якому розміщуються нейрони.
     *
     * @return шар полотна, на якому розміщуються нейрони.
     */
    public Pane nodeLayer() {
        return nodeLayer;
    }

    /**
     * Повертає шар екранних накладок — меню, ручок обертання та інших елементів поверх карти.
     *
     * @return шар екранних накладок — меню, ручок обертання та інших елементів поверх карти.
     */
    public Pane overlayLayer() {
        return overlayLayer;
    }

    /**
     * Застосовує масштаб і зміщення камери до шарів карти.
     *
     * @param zoom коефіцієнт масштабування.
     * @param panX горизонтальне зміщення камери.
     * @param panY вертикальне зміщення камери.
     */
    public void setWorldTransform(
            double zoom,
            double panX,
            double panY
    ) {
        worldScale.setX(zoom);
        worldScale.setY(zoom);
        world.setTranslateX(panX);
        world.setTranslateY(panY);

        redrawGrid(zoom, panX, panY);
    }

    /**
     * Перемальовує координатну сітку відповідно до поточних параметрів камери.
     *
     * @param zoom коефіцієнт масштабування.
     * @param panX горизонтальне зміщення камери.
     * @param panY вертикальне зміщення камери.
     */
    public void redrawGrid(
            double zoom,
            double panX,
            double panY
    ) {
        gridRenderer.redraw(
                viewport.getWidth(),
                viewport.getHeight(),
                zoom,
                panX,
                panY
        );
    }

    /**
     * Перемальовує координатну сітку відповідно до поточних параметрів камери.
     */
    private void redrawGrid() {
        redrawGrid(
                worldScale.getX(),
                world.getTranslateX(),
                world.getTranslateY()
        );
    }
}
