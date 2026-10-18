package com.example.neuronmap.view.connection;

import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.NeuronVisualGeometry;
import javafx.geometry.Point2D;
import javafx.scene.shape.Line;

import java.util.Objects;

/**
 * Відповідає за створення, геометрію, показ і приховування тимчасової лінії під час створення зв'язку.
 */
public final class ConnectionPreviewView {

    private final WorkspaceView workspace;
    private Line previewLine;
    private NeuronView source;
    private Point2D cursorPosition;

    /**
     * Створює подання прев'ю та зберігає полотно, на якому відображатиметься тимчасова лінія.
     *
     * @param workspace полотно редактора з шаром накладок.
     * @throws NullPointerException якщо полотно не задано.
     */
    public ConnectionPreviewView(WorkspaceView workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /**
     * Показує тимчасову лінію від виходу нейрона до курсора; повторний виклик оновлює наявну лінію.
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
     * Оновлює джерело й кінець видимої лінії; приховане прев'ю не додається на полотно.
     *
     * @param source поточне подання нейрона-джерела.
     * @param cursorPosition поточна позиція курсора в координатах полотна.
     * @throws NullPointerException якщо джерело або позицію курсора не задано для активного прев'ю.
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
     * Прибирає лінію із шару накладок і звільняє посилання на джерело та позицію курсора.
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
     * Створює лінію з CSS-класом, що належить лише попередньому перегляду зв'язку.
     *
     * @return підготовлена лінія.
     */
    private Line createPreviewLine() {
        Line line = new Line();
        line.getStyleClass().add("connection-preview-line");
        line.setMouseTransparent(true);
        return line;
    }

    /**
     * Перераховує координати лінії з урахуванням геометрії нейрона й трансформацій камери.
     */
    private void updateGeometry() {
        if (previewLine == null || source == null || cursorPosition == null) {
            return;
        }
        double outputX = NeuronVisualGeometry.outputTipX(source.isDirectionReversed());
        Point2D startScene = source.localToScene(outputX, NeuronView.HEIGHT / 2.0);
        Point2D start = workspace.node().sceneToLocal(startScene);
        previewLine.setStartX(start.getX());
        previewLine.setStartY(start.getY());
        previewLine.setEndX(cursorPosition.getX());
        previewLine.setEndY(cursorPosition.getY());
    }
}
