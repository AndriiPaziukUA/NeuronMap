package com.example.neuronmap.view;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/**
 * Створює робочу область, у якій відображається карта нейронів.
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

    /**
     * Повертає результат операції «відображення».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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
     * Повертає результат операції «вузол».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public StackPane node() {
        return viewport;
    }

    /**
     * Повертає результат операції «група».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Pane groupLayer() {
        return groupLayer;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Pane edgeLayer() {
        return edgeLayer;
    }

    /**
     * Повертає результат операції «імпульс».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Pane pulseLayer() {
        return pulseLayer;
    }

    /**
     * Повертає результат операції «вузол».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Pane nodeLayer() {
        return nodeLayer;
    }

    /**
     * Повертає результат операції «накладка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Pane overlayLayer() {
        return overlayLayer;
    }

    /**
     * Задає або оновлює значення, повʼязані з «карта».
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
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
     * Виконує операцію «відповідну операцію».
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
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
     * Виконує операцію «відповідну операцію».
     */
    private void redrawGrid() {
        redrawGrid(
                worldScale.getX(),
                world.getTranslateX(),
                world.getTranslateY()
        );
    }
}
