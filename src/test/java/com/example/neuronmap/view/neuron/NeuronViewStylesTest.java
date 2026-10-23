package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє кольори, стани вибору та наведення, а також прямокутник назви нейрона. */
final class NeuronViewStylesTest {

    private static final double EPSILON = 0.001;
    private static final PseudoClass HOVER_PSEUDO_CLASS = PseudoClass.getPseudoClass("hover");

    /**
     * Переконується, що вибір додає світіння, наведення лише змінює колір рамки, а її товщина не змінюється.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void cssControlsNeuronTypeSelectionGlowHoverBorderAndStableThickness() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView excitatory = createView("style-excitatory", NeuronType.EXCITATORY);
            NeuronView inhibitory = createView("style-inhibitory", NeuronType.INHIBITORY);
            HBox root = new HBox(excitatory, inhibitory);
            Scene scene = new Scene(root, 400.0, 200.0);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            NeuronViewStylesTest.class.getResource("/app.css"),
                            "Missing /app.css test resource"
                    ).toExternalForm()
            );
            root.applyCss();

            Rectangle excitatoryBody = (Rectangle) excitatory.getChildren().getFirst();
            Rectangle inhibitoryBody = (Rectangle) inhibitory.getChildren().getFirst();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-excitatory"));
            assertTrue(inhibitoryBody.getStyleClass().contains("neuron-card-inhibitory"));
            assertEquals(Color.web("#328b55"), excitatoryBody.getFill());
            assertEquals(Color.web("#646971"), inhibitoryBody.getFill());
            assertEquals(Color.web("#25292f"), excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertNull(excitatoryBody.getEffect(), "Невибраний нейрон не повинен світитися");
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            excitatory.refreshVisuals(true);
            root.applyCss();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-selected"));
            assertEquals(Color.WHITE, excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertInstanceOf(DropShadow.class, excitatoryBody.getEffect(),
                    "Вибраний нейрон має залишатися підсвіченим, поки активний вибір");
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            // Повторне оновлення імітує такти симуляції й не повинно прибирати світіння або змінювати геометрію.
            excitatory.refreshVisuals(true);
            root.applyCss();
            assertInstanceOf(DropShadow.class, excitatoryBody.getEffect());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertBodyRemainsAtLocalOrigin(excitatoryBody);

            inhibitory.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            root.applyCss();
            assertEquals(Color.WHITE, inhibitoryBody.getStroke(),
                    "Наведення має показувати білу рамку навколо невибраного нейрона");
            assertEquals(2.0, inhibitoryBody.getStrokeWidth(), EPSILON);
            assertNull(inhibitoryBody.getEffect(), "Наведення не повинно підсвічувати тіло нейрона");
            assertBodyRemainsAtLocalOrigin(inhibitoryBody);

            inhibitory.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, false);
            root.applyCss();
            assertEquals(Color.web("#25292f"), inhibitoryBody.getStroke(),
                    "Після завершення наведення має відновитися звичайна рамка");
            assertEquals(2.0, inhibitoryBody.getStrokeWidth(), EPSILON);
            assertNull(inhibitoryBody.getEffect());

            excitatory.refreshVisuals(false);
            root.applyCss();
            assertEquals(Color.web("#25292f"), excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertNull(excitatoryBody.getEffect(), "Після зняття вибору світіння має зникнути");
            assertBodyRemainsAtLocalOrigin(excitatoryBody);
        });
    }

    /** Перевіряє появу назви лише під час наведення та її приховування після натискання або вибору нейрона. */
    @Test
    void keepsNameVisibleOnPressAndSelectionAndHidesOnlyDuringRotation() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView("named-neuron", NeuronType.EXCITATORY);
            view.model().setName("Вхід <> 🚀");
            assertFalse(view.isNameLabelVisibleForTest());

            view.fireEvent(mouseEvent(MouseEvent.MOUSE_ENTERED));
            assertTrue(view.isNameLabelVisibleForTest());
            assertEquals("Вхід <> 🚀", view.nameLabelForTest().getText());

            view.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED));
            assertTrue(view.isNameLabelVisibleForTest(), "Клацання не повинно приховувати назву");
            view.fireEvent(mouseEvent(MouseEvent.MOUSE_MOVED));
            assertTrue(view.isNameLabelVisibleForTest());

            view.refreshVisuals(true);
            assertTrue(view.isNameLabelVisibleForTest(), "Назва має залишатися разом із ручкою вибраного нейрона");
            view.setNameHiddenWhileRotating(true);
            assertFalse(view.isNameLabelVisibleForTest(), "Назва ховається на час обертання");
            view.setNameHiddenWhileRotating(false);
            assertTrue(view.isNameLabelVisibleForTest(), "Після відпускання ручки назва з'являється знову");
            view.fireEvent(mouseEvent(MouseEvent.MOUSE_EXITED));
            assertFalse(view.isNameLabelVisibleForTest());
            view.disposeParameterTooltip();
        });
    }

    /**
     * Перевіряє, що підпис лишається горизонтальним і розташовується над повернутим нейроном зі сталим екранним відступом.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void positionsNameAboveRotatedNeuronWithFixedScreenGapWhenZoomed() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("rotated-name", NeuronType.EXCITATORY, 0);
            neuron.setName("Rotated label");
            NeuronView view = new NeuronView(
                    new NeuronPresentation(neuron, 40.0, 50.0, 67.0));
            StackPane root = new StackPane(view);
            Scale cameraScale = new Scale(1.75, 1.75, 0.0, 0.0);
            root.getTransforms().add(cameraScale);
            Scene scene = new Scene(root, 800.0, 600.0);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            NeuronViewStylesTest.class.getResource("/app.css"),
                            "Missing /app.css test resource"
                    ).toExternalForm()
            );
            root.applyCss();
            root.layout();

            view.fireEvent(mouseEvent(MouseEvent.MOUSE_ENTERED));
            root.applyCss();
            root.layout();

            Point2D[] corners = {
                    view.localToScene(0.0, 0.0),
                    view.localToScene(NeuronView.WIDTH, 0.0),
                    view.localToScene(0.0, NeuronView.HEIGHT),
                    view.localToScene(NeuronView.WIDTH, NeuronView.HEIGHT)
            };
            double topY = java.util.Arrays.stream(corners)
                    .mapToDouble(Point2D::getY)
                    .min()
                    .orElseThrow();
            Label label = view.nameLabelForTest();
            Bounds labelBounds = label.localToScene(label.getBoundsInLocal());
            double neuronCenterX = view.localToScene(
                    NeuronView.WIDTH / 2.0, NeuronView.HEIGHT / 2.0).getX();

            assertTrue(view.isNameLabelVisibleForTest());
            assertEquals(topY - NeuronOverlayPositioner.MENU_GAP, labelBounds.getMaxY(), 1.0);
            assertEquals(neuronCenterX, labelBounds.getMinX() + labelBounds.getWidth() / 2.0, 1.0);
            assertEquals(0.0, label.getRotate() + view.getRotate(), EPSILON);
            assertEquals(1.0 / 1.75, label.getScaleX(), 0.01);
            assertEquals(1.0 / 1.75, label.getScaleY(), 0.01);

            // Імітує звичайний callback оновлення екранних накладок після zoom без руху миші.
            cameraScale.setX(2.25);
            cameraScale.setY(2.25);
            view.refreshHoverNameGeometry();
            root.applyCss();
            root.layout();

            Bounds zoomedLabelBounds = label.localToScene(label.getBoundsInLocal());
            Point2D zoomedNeuronCenter = view.localToScene(
                    NeuronView.WIDTH / 2.0, NeuronView.HEIGHT / 2.0);
            Point2D[] zoomedCorners = {
                    view.localToScene(0.0, 0.0),
                    view.localToScene(NeuronView.WIDTH, 0.0),
                    view.localToScene(0.0, NeuronView.HEIGHT),
                    view.localToScene(NeuronView.WIDTH, NeuronView.HEIGHT)
            };
            double zoomedTopY = java.util.Arrays.stream(zoomedCorners)
                    .mapToDouble(Point2D::getY)
                    .min()
                    .orElseThrow();
            assertEquals(1.0 / 2.25, label.getScaleX(), 0.01);
            assertEquals(1.0 / 2.25, label.getScaleY(), 0.01);
            assertEquals(zoomedTopY - NeuronOverlayPositioner.MENU_GAP,
                    zoomedLabelBounds.getMaxY(), 1.0);
            assertEquals(labelBounds.getWidth(), zoomedLabelBounds.getWidth(), 1.0,
                    "Підпис має негайно зберігати екранну ширину після zoom");
            assertEquals(labelBounds.getHeight(), zoomedLabelBounds.getHeight(), 1.0,
                    "Підпис має негайно зберігати екранну висоту після zoom");
            assertEquals(zoomedNeuronCenter.getX(),
                    zoomedLabelBounds.getMinX() + zoomedLabelBounds.getWidth() / 2.0, 1.0);
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що нейрон без назви не показує порожнього прямокутника навіть під час наведення. */
    @Test
    void doesNotDisplayNamePopupWhenNameIsNull() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView("unnamed-neuron", NeuronType.EXCITATORY);
            view.fireEvent(mouseEvent(MouseEvent.MOUSE_ENTERED));

            assertFalse(view.isNameLabelVisibleForTest());
            view.disposeParameterTooltip();
        });
    }

    /**
     * Перевіряє, що CSS-рамка не зсуває прямокутник від незмінного локального початку координат.
     *
     * @param body графічне тіло нейрона.
     */
    private static void assertBodyRemainsAtLocalOrigin(Rectangle body) {
        assertEquals(0.0, body.getLayoutX(), EPSILON,
                "CSS-рамка не повинна змінювати горизонтальне розташування тіла");
        assertEquals(0.0, body.getLayoutY(), EPSILON,
                "CSS-рамка не повинна змінювати вертикальне розташування тіла");
        assertEquals(0.0, body.getX(), EPSILON,
                "Ліва межа тіла має залишатися в локальній координаті X = 0");
        assertEquals(0.0, body.getY(), EPSILON,
                "Верхня межа тіла має залишатися в локальній координаті Y = 0");
    }

    /**
     * Створює нейронний вузол заданого типу для перевірки таблиці стилів.
     *
     * @param id унікальний ідентифікатор нейрона.
     * @param type тип нейрона.
     * @return графічне подання нейрона.
     */
    private static NeuronView createView(String id, NeuronType type) {
        Neuron neuron = new Neuron(id, type, 0);
        return new NeuronView(new NeuronPresentation(neuron, 40.0, 50.0, 0.0));
    }

    /** Створює синтетичну подію миші для перевірки реакції подання без відображення окремого вікна. */
    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> eventType) {
        return new MouseEvent(
                eventType,
                0.0, 0.0, 0.0, 0.0,
                MouseButton.NONE, 0,
                false, false, false, false,
                false, false, false,
                false, false, false, null
        );
    }
}
