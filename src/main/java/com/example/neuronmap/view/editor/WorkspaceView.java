package com.example.neuronmap.view.editor;

import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

import java.util.Objects;

/** Збирає шари полотна карти, керує клавіатурним фокусом і перемальовує координатну сітку. */
public final class WorkspaceView {

    private final StackPane viewport = new StackPane();
    private final Canvas gridCanvas = new Canvas();
    private final GridRenderer gridRenderer = new GridRenderer(gridCanvas);

    private final Pane world = new Pane();
    private final Pane groupLayer = new Pane();
    private final Pane edgeLayer = new Pane();
    private final Pane pulseLayer = new Pane();
    private final Pane nodeLayer = new Pane();
    private final Pane overlayLayer = new Pane();

    private final Rectangle clip = new Rectangle();
    private final Scale worldScale = new Scale(1.0, 1.0, 0.0, 0.0);
    private final ChangeListener<Number> viewportWidthListener =
            (observable, oldValue, newValue) -> redrawGrid();
    private final ChangeListener<Number> viewportHeightListener =
            (observable, oldValue, newValue) -> redrawGrid();
    private final EventHandler<MouseEvent> focusRequestHandler = this::handleMousePressed;
    private boolean disposed;

    /** Створює шари робочої області, налаштовує фокус дошки та реєструє слухачі розміру. */
    public WorkspaceView() {
        viewport.getStyleClass().add("canvas");
        viewport.setPickOnBounds(true);
        viewport.setFocusTraversable(true);
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED, focusRequestHandler);

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

        world.getChildren().addAll(groupLayer, edgeLayer, pulseLayer, nodeLayer);
        viewport.getChildren().addAll(gridCanvas, world, overlayLayer);

        StackPane.setAlignment(gridCanvas, Pos.TOP_LEFT);
        StackPane.setAlignment(world, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayLayer, Pos.TOP_LEFT);

        viewport.widthProperty().addListener(viewportWidthListener);
        viewport.heightProperty().addListener(viewportHeightListener);
    }

    /**
     * Від'єднує слухачі розміру, фільтр фокуса та внутрішні прив'язки робочої області.
     * Повторний виклик безпечний і не змінює поточне графічне представлення.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        viewport.removeEventFilter(MouseEvent.MOUSE_PRESSED, focusRequestHandler);
        viewport.widthProperty().removeListener(viewportWidthListener);
        viewport.heightProperty().removeListener(viewportHeightListener);
        clip.widthProperty().unbind();
        clip.heightProperty().unbind();
    }

    /**
     * Переміщує клавіатурний фокус на вузол усередині робочої області.
     * Метод зберігає централізоване керування фокусом, не змінюючи маршрутизацію гарячих клавіш.
     *
     * @param target вузол робочої області, який має отримати клавіатурний фокус.
     * @throws NullPointerException якщо вузол не задано.
     * @throws IllegalArgumentException якщо вузол не належить цій робочій області.
     * @throws IllegalStateException якщо робочу область уже звільнено.
     */
    public void focusNode(Node target) {
        Objects.requireNonNull(target, "target");
        if (disposed) {
            throw new IllegalStateException("Workspace view is disposed");
        }
        if (!belongsToViewport(target)) {
            throw new IllegalArgumentException("Focus target does not belong to this workspace");
        }
        target.setFocusTraversable(true);
        target.requestFocus();
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
     * Повертає шар полотна, на якому відображаються зв'язки між нейронами.
     *
     * @return шар полотна, на якому відображаються зв'язки між нейронами.
     */
    public Pane edgeLayer() {
        return edgeLayer;
    }

    /**
     * Повертає шар полотна для анімаційних імпульсів на зв'язках.
     *
     * @return шар полотна для анімаційних імпульсів на зв'язках.
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
    public void setWorldTransform(double zoom, double panX, double panY) {
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
    public void redrawGrid(double zoom, double panX, double panY) {
        gridRenderer.redraw(viewport.getWidth(), viewport.getHeight(), zoom, panX, panY);
    }

    /** Переміщує клавіатурний фокус на дошку, не забираючи його в елементів введення чи кнопок. */
    private void handleMousePressed(MouseEvent event) {
        if (!disposed && !isInteractiveTarget(event.getTarget())) {
            viewport.requestFocus();
        }
    }

    /**
     * Перевіряє, чи є переданий вузол дочірнім елементом поточної робочої області.
     *
     * @param target вузол, належність якого перевіряється.
     * @return {@code true}, якщо вузол є коренем або нащадком робочої області.
     */
    private boolean belongsToViewport(Node target) {
        Node current = target;
        while (current != null) {
            if (current == viewport) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Перевіряє, чи натиснули інтерактивний елемент, який має зберігати власний фокус.
     *
     * @param target ціль події миші.
     * @return {@code true}, якщо ціль або її предок є кнопкою чи текстовим полем.
     */
    private boolean isInteractiveTarget(Object target) {
        if (!(target instanceof Node targetNode)) {
            return false;
        }
        Node current = targetNode;
        while (current != null && current != viewport) {
            if (current instanceof Button || current instanceof TextInputControl) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** Перемальовує координатну сітку відповідно до поточних параметрів камери. */
    private void redrawGrid() {
        redrawGrid(worldScale.getX(), world.getTranslateX(), world.getTranslateY());
    }
}
