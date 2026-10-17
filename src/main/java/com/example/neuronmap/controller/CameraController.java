package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.util.CameraWorldCenter;
import com.example.neuronmap.util.GeometryUtils;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.animation.PauseTransition;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Обробляє панорамування й масштабування полотна, координати камери та відкладене збереження її змін. */
public final class CameraController {

    private final EditorState state;
    private final WorkspaceView workspace;
    private final Runnable refreshOverlay;
    private final BooleanSupplier interactionActive;
    private final Predicate<Object> interactiveTarget;
    private final Consumer<String> status;
    private final Consumer<Point2D> cameraCenterWorldConsumer;
    private final LocalizationService localization;
    private final double zoomFactor;
    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(250));

    private boolean panning;
    private boolean suspended;
    private boolean savePaused;
    private double lastMouseX;
    private double lastMouseY;

    /**
     * Створює контролер камери та налаштовує його залежності.
     *
     * @param state змінний стан камери редактора.
     * @param workspace полотно редактора.
     * @param refreshOverlay дія оновлення положення накладок.
     * @param save дія збереження стану редактора.
     * @param interactionActive перевірка активної взаємодії з полотном.
     * @param interactiveTarget перевірка інтерактивної цілі події.
     * @param status дія показу повідомлення в рядку стану.
     * @param cameraCenterWorldConsumer отримувач координат центра камери у світовій системі.
     * @param zoomFactor коефіцієнт зміни масштабу за один крок прокручування.
     * @param localization служба локалізації інтерфейсу; за відсутності використовується українська локаль.
     * @throws IllegalArgumentException якщо коефіцієнт масштабу не є скінченним числом, більшим за одиницю.
     */
    public CameraController(
            EditorState state,
            WorkspaceView workspace,
            Runnable refreshOverlay,
            Runnable save,
            BooleanSupplier interactionActive,
            Predicate<Object> interactiveTarget,
            Consumer<String> status,
            Consumer<Point2D> cameraCenterWorldConsumer,
            double zoomFactor,
            LocalizationService localization
    ) {
        this.state = state;
        this.workspace = workspace;
        this.refreshOverlay = refreshOverlay;
        this.interactionActive = interactionActive;
        this.interactiveTarget = interactiveTarget;
        this.status = status;
        this.cameraCenterWorldConsumer = cameraCenterWorldConsumer;
        this.localization = localization == null
                ? new LocalizationService(java.util.Locale.forLanguageTag("uk"))
                : localization;
        if (!Double.isFinite(zoomFactor) || zoomFactor <= 1.0) {
            throw new IllegalArgumentException("zoomFactor must be > 1");
        }
        this.zoomFactor = zoomFactor;
        saveDebounce.setOnFinished(event -> save.run());
    }

    /** Реєструє обробники миші та прокручування для керування камерою. */
    public void install() {
        workspace.node().addEventFilter(MouseEvent.MOUSE_PRESSED, this::handlePressed);
        workspace.node().addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDragged);
        workspace.node().addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleReleased);
        workspace.node().addEventHandler(ScrollEvent.SCROLL, this::handleZoom);
        workspace.node().widthProperty().addListener(
                (observable, oldValue, newValue) -> refreshCameraCoordinates());
        workspace.node().heightProperty().addListener(
                (observable, oldValue, newValue) -> refreshCameraCoordinates());
    }

    /** Застосовує поточний масштаб і зміщення зі стану редактора до полотна. */
    public void apply() {
        workspace.setWorldTransform(state.zoom(), state.panX(), state.panY());
        refreshOverlay.run();
        refreshCameraCoordinates();
    }

    /** Призупиняє збереження камери, завершує перетягування та зберігає залишок таймера відкладеного запису. */
    public void pausePendingSave() {
        if (suspended) {
            return;
        }
        suspended = true;
        if (panning) {
            panning = false;
            workspace.node().setCursor(Cursor.DEFAULT);
            saveDebounce.playFromStart();
        }
        if (saveDebounce.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            saveDebounce.pause();
            savePaused = true;
        }
    }

    /** Відновлює відкладене збереження камери після зняття паузи. */
    public void resumePendingSave() {
        if (!suspended) {
            return;
        }
        suspended = false;
        if (savePaused) {
            savePaused = false;
            saveDebounce.play();
        }
    }

    /** Скасовує відкладене збереження камери та скидає панорамування й тимчасовий курсор перетягування. */
    public void cancelPendingSave() {
        saveDebounce.stop();
        savePaused = false;
        panning = false;
        workspace.node().setCursor(Cursor.DEFAULT);
    }

    /** Обробляє початок панорамування, якщо подію не адресовано іншому режиму чи елементу керування. */
    private void handlePressed(MouseEvent event) {
        if (suspended
                || event.getButton() != MouseButton.PRIMARY
                || interactionActive.getAsBoolean()
                || interactiveTarget.test(event.getTarget())) {
            return;
        }
        panning = true;
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        workspace.node().setCursor(Cursor.CLOSED_HAND);
        event.consume();
    }

    /** Оновлює зміщення камери під час перетягування полотна. */
    private void handleDragged(MouseEvent event) {
        if (suspended || !panning) {
            return;
        }
        double deltaX = event.getX() - lastMouseX;
        double deltaY = event.getY() - lastMouseY;
        state.setPanX(state.panX() + deltaX);
        state.setPanY(state.panY() + deltaY);
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        apply();
        event.consume();
    }

    /** Завершує панорамування та планує збереження нового положення камери. */
    private void handleReleased(MouseEvent event) {
        if (suspended || !panning) {
            return;
        }
        panning = false;
        workspace.node().setCursor(Cursor.DEFAULT);
        scheduleSave();
    }

    /** Змінює масштаб із прив'язкою до позиції курсора та показує локалізоване повідомлення. */
    private void handleZoom(ScrollEvent event) {
        if (suspended || event.getDeltaY() == 0.0) {
            return;
        }
        Point2D cursor = new Point2D(event.getX(), event.getY());
        double oldZoom = state.zoom();
        double factor = event.getDeltaY() > 0.0 ? zoomFactor : 1.0 / zoomFactor;
        double newZoom = GeometryUtils.clampZoom(oldZoom * factor);
        if (Double.compare(oldZoom, newZoom) == 0) {
            return;
        }

        double worldX = (cursor.getX() - state.panX()) / oldZoom;
        double worldY = (cursor.getY() - state.panY()) / oldZoom;
        state.setZoom(newZoom);
        state.setPanX(cursor.getX() - worldX * newZoom);
        state.setPanY(cursor.getY() - worldY * newZoom);
        apply();
        scheduleSave();
        status.accept(localization.text("status.scale", formatZoom(newZoom)));
        event.consume();
    }

    /** Оновлює координати центра камери після зміни розміру полотна або його трансформації. */
    private void refreshCameraCoordinates() {
        if (cameraCenterWorldConsumer == null) {
            return;
        }
        double width = workspace.node().getWidth();
        double height = workspace.node().getHeight();
        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        cameraCenterWorldConsumer.accept(CameraWorldCenter.calculate(
                width, height, state.zoom(), state.panX(), state.panY()));
    }

    /** Планує збереження камери та залишає таймер призупиненим, якщо редактор у модальному стані. */
    private void scheduleSave() {
        saveDebounce.playFromStart();
        if (suspended) {
            saveDebounce.pause();
            savePaused = true;
        }
    }

    /** Форматує масштаб як відсоток без зайвих десяткових знаків. */
    private static String formatZoom(double zoom) {
        double percent = zoom * 100.0;
        return Math.abs(percent - Math.rint(percent)) < 0.0001
                ? String.format(java.util.Locale.ROOT, "%.0f%%", percent)
                : String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }
}
