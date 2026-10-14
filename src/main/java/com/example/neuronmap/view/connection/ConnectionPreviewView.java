package com.example.neuronmap.view.connection;

import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import java.util.Objects;

/**
 * Відповідає за створення, геометрію, показ і приховування тимчасової лінії під час створення зв’язку.
 */
public final class ConnectionPreviewView {

    private static final Color PREVIEW_COLOR = Color.web("#c5ccd6");
    private static final double PREVIEW_STROKE_WIDTH = 2.2;
    private static final double DASH_LENGTH = 9.0;
    private static final double DASH_GAP = 7.0;

    private final WorkspaceView workspace;

    private Line previewLine;
    private NeuronView source;
    private Point2D cursorPosition;

    /**
     * Створює подання прев’ю та зберігає полотно, на якому відображатиметься тимчасова лінія.
     *
     * @param workspace полотно редактора з шаром накладок.
     * @throws NullPointerException якщо полотно не задано.
     */
    public ConnectionPreviewView(WorkspaceView workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /**
     * Показує тимчасову лінію від виходу нейрона до курсора.
     * Повторний виклик оновлює наявне прев’ю, не додаючи дубль графічного вузла.
     *
     * @param source нейрон, від виходу якого починається лінія.
     * @param cursorPosition позиція курсора в координатах полотна.
     * @throws NullPointerException якщо джерело або позицію курсора не задано.
     */
    public void show(NeuronView source, Point2D cursorPosition) {
        this.source = Objects.requireNonNull(source, "source");
        this.cursorPosition = Objects.requireNonNull(cursorPosition, "cursorPosition");

        if (previewLine == null) {
            previewLine = createPreviewLine();
        }
        if (!workspace.overlayLayer().getChildren().contains(previewLine)) {
            workspace.overlayLayer().getChildren().add(previewLine);
        }

        updateGeometry();
    }

    /**
     * Оновлює джерело та позицію кінця видимої лінії, не змінюючи логіку створення зв’язку.
     * Виклик для прихованого прев’ю нічого не додає на полотно.
     *
     * @param source поточне подання нейрона-джерела.
     * @param cursorPosition поточна позиція курсора в координатах полотна.
     * @throws NullPointerException якщо джерело або позицію курсора не задано під час показаного прев’ю.
     */
    public void update(NeuronView source, Point2D cursorPosition) {
        if (previewLine == null) {
            return;
        }

        this.source = Objects.requireNonNull(source, "source");
        this.cursorPosition = Objects.requireNonNull(cursorPosition, "cursorPosition");
        updateGeometry();
    }

    /**
     * Прибирає графічну лінію із шару накладок і звільняє посилання на джерело та курсор.
     */
    public void hide() {
        if (previewLine != null) {
            workspace.overlayLayer().getChildren().remove(previewLine);
        }
        previewLine = null;
        source = null;
        cursorPosition = null;
    }

    /**
     * Створює графічну лінію та налаштовує її стиль і прозорість для подій миші.
     *
     * @return підготовлена лінія прев’ю.
     */
    private Line createPreviewLine() {
        Line line = new Line();
        line.setStroke(PREVIEW_COLOR);
        line.setStrokeWidth(PREVIEW_STROKE_WIDTH);
        line.getStrokeDashArray().setAll(DASH_LENGTH, DASH_GAP);
        line.setMouseTransparent(true);
        return line;
    }

    /**
     * Перераховує координати початку з урахуванням геометрії нейрона й трансформацій камери,
     * а кінець установлює в передану позицію курсора.
     */
    private void updateGeometry() {
        if (previewLine == null || source == null || cursorPosition == null) {
            return;
        }

        double outputX = NeuronVisualGeometry.outputTipX(source.isDirectionReversed());
        Point2D startScene = source.localToScene(
                outputX,
                NeuronView.HEIGHT / 2.0
        );
        Point2D start = workspace.node().sceneToLocal(startScene);

        previewLine.setStartX(start.getX());
        previewLine.setStartY(start.getY());
        previewLine.setEndX(cursorPosition.getX());
        previewLine.setEndY(cursorPosition.getY());
    }
}
