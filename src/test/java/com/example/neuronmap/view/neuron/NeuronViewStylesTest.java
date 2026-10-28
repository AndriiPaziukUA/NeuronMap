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
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.transform.Scale;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє оформлення нейронів, ізоляцію наведення та геометрію карток у шухлядці структури. */
final class NeuronViewStylesTest {

    private static final double EPSILON = 0.001;
    private static final PseudoClass HOVER_PSEUDO_CLASS = PseudoClass.getPseudoClass("hover");

    /** Перевіряє палітру типів, вибір, наведення та контур картки, що не виходить за її межі. */
    @Test
    void cssControlsNeuronTypeSelectionGlowHoverBorderAndStableThickness() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView excitatory = createView("style-excitatory", NeuronType.EXCITATORY);
            NeuronView inhibitory = createView("style-inhibitory", NeuronType.INHIBITORY);
            NeuronView structure = createView("style-structure", NeuronType.STRUCTURE);
            HBox root = new HBox(excitatory, inhibitory, structure);
            Scene scene = new Scene(root, 600.0, 200.0);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            NeuronViewStylesTest.class.getResource("/app.css"),
                            "Missing /app.css test resource"
                    ).toExternalForm()
            );
            root.applyCss();

            Rectangle excitatoryBody = (Rectangle) excitatory.getChildren().getFirst();
            Rectangle inhibitoryBody = (Rectangle) inhibitory.getChildren().getFirst();
            Rectangle structureBody = (Rectangle) structure.getChildren().getFirst();
            assertTrue(excitatoryBody.getStyleClass().contains("neuron-card-excitatory"));
            assertTrue(inhibitoryBody.getStyleClass().contains("neuron-card-inhibitory"));
            assertTrue(structureBody.getStyleClass().contains("neuron-card-structure"));
            assertEquals(Color.web("#328b55"), excitatoryBody.getFill());
            assertEquals(Color.web("#646971"), inhibitoryBody.getFill());
            assertEquals(Color.web("#f59e0b"), structureBody.getFill());
            assertEquals(Color.web("#25292f"), excitatoryBody.getStroke());
            assertEquals(2.0, excitatoryBody.getStrokeWidth(), EPSILON);
            assertEquals(StrokeType.CENTERED, excitatoryBody.getStrokeType(),
                    "Центрований контур має залишатися в межах картки завдяки внутрішньому відступу тіла");
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

            inhibitoryBody.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            root.applyCss();
            assertEquals(Color.WHITE, inhibitoryBody.getStroke(),
                    "Наведення має показувати білу рамку навколо самої картки нейрона");
            assertEquals(2.0, inhibitoryBody.getStrokeWidth(), EPSILON);
            assertNull(inhibitoryBody.getEffect(), "Наведення не повинно підсвічувати тіло нейрона");
            assertBodyRemainsAtLocalOrigin(inhibitoryBody);

            inhibitoryBody.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, false);
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
            excitatory.disposeParameterTooltip();
            inhibitory.disposeParameterTooltip();
            structure.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що наведення на вкладений нейрон не підсвічує тіло його структурного предка. */
    @Test
    void hoveringOverStructureMemberDoesNotHighlightStructureContainer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView structure = createView("hover-structure", NeuronType.STRUCTURE);
            NeuronView member = createView("hover-member", NeuronType.EXCITATORY);
            structure.setStructureMembers(List.of(member));
            structure.toggleStructureDrawer();

            StackPane root = new StackPane(structure);
            Scene scene = new Scene(root, 400.0, 300.0);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            NeuronViewStylesTest.class.getResource("/app.css"),
                            "Missing /app.css test resource"
                    ).toExternalForm()
            );
            root.applyCss();
            root.layout();

            Rectangle structureBody = (Rectangle) structure.getChildren().getFirst();
            Rectangle memberBody = (Rectangle) member.getChildren().getFirst();
            structure.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            member.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            root.applyCss();

            assertEquals(Color.web("#25292f"), structureBody.getStroke(),
                    "Hover псевдоклас контейнера не повинен підсвічувати його картку через дочірній вузол");
            assertEquals(Color.web("#25292f"), memberBody.getStroke(),
                    "Hover контейнера нейрона не повинен сам по собі стилізувати внутрішню картку");

            memberBody.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, true);
            root.applyCss();
            assertEquals(Color.WHITE, memberBody.getStroke(), "Має підсвічуватися саме наведена картка");
            assertEquals(Color.web("#25292f"), structureBody.getStroke(),
                    "Наведення на картку учасника не повинно підсвічувати зовнішню структуру");

            structure.disposeParameterTooltip();
            member.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що контури карток у шухлядці лежать у межах рядків і не перекривають окрему рамку контейнера. */
    @Test
    void structureDrawerKeepsMemberFramesWithinAdjacentRows() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView structure = createView("drawer-layout-structure", NeuronType.STRUCTURE);
            NeuronView first = createView("drawer-layout-first", NeuronType.EXCITATORY);
            NeuronView second = createView("drawer-layout-second", NeuronType.INHIBITORY);
            structure.setStructureMembers(List.of(first, second));
            structure.toggleStructureDrawer();

            StackPane root = new StackPane(structure);
            Scene scene = new Scene(root, 500.0, 400.0);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            NeuronViewStylesTest.class.getResource("/app.css"),
                            "Missing /app.css test resource"
                    ).toExternalForm()
            );
            root.applyCss();
            root.layout();

            NeuronStructureDrawerView drawer = structure.getChildren().stream()
                    .filter(NeuronStructureDrawerView.class::isInstance)
                    .map(NeuronStructureDrawerView.class::cast)
                    .findFirst()
                    .orElseThrow();
            Rectangle firstBody = (Rectangle) first.getChildren().getFirst();
            Rectangle secondBody = (Rectangle) second.getChildren().getFirst();

            assertNotNull(drawer.getBorder(), "Шухлядка має зберігати окремий контракт контуру");
            Path outline = drawer.getChildren().stream()
                    .filter(Path.class::isInstance)
                    .map(Path.class::cast)
                    .findFirst()
                    .orElseThrow();
            Bounds outlineBounds = outline.getBoundsInLocal();
            assertTrue(outline.isMouseTransparent(), "Контур шухлядки не повинен перехоплювати події миші");
            assertTrue(drawer.getChildren().indexOf(outline) < drawer.getChildren().indexOf(first),
                    "Контур шухлядки має малюватися позаду карток, щоб не перекривати порти");
            assertTrue(outlineBounds.getMinX() < 0.0,
                    "Ліва лінія контуру має бути зовні карток, а не поверх їхніх рамок");
            assertTrue(outlineBounds.getMaxX() > NeuronView.WIDTH,
                    "Права лінія контуру має бути зовні карток, а не поверх їхніх рамок");
            assertTrue(outlineBounds.getMaxY() > drawer.getHeight(),
                    "Нижня лінія контуру має бути поза останнім рядком нейронів");
            assertEquals(NeuronView.WIDTH, drawer.getWidth(), EPSILON);
            assertEquals(NeuronView.HEIGHT * 2.0, drawer.getHeight(), EPSILON);
            assertEquals(0.0, first.getLayoutX(), EPSILON);
            assertEquals(0.0, first.getLayoutY(), EPSILON);
            assertEquals(0.0, second.getLayoutX(), EPSILON);
            assertEquals(NeuronView.HEIGHT, second.getLayoutY(), EPSILON,
                    "Наступний рядок має починатися після попереднього, без накладання");

            assertEquals(StrokeType.CENTERED, firstBody.getStrokeType());
            assertEquals(StrokeType.CENTERED, secondBody.getStrokeType());
            assertEquals(0.0, firstBody.getBoundsInLocal().getMinX(), EPSILON);
            assertEquals(NeuronView.WIDTH, firstBody.getBoundsInLocal().getMaxX(), EPSILON);
            assertEquals(0.0, firstBody.getBoundsInLocal().getMinY(), EPSILON);
            assertEquals(NeuronView.HEIGHT, firstBody.getBoundsInLocal().getMaxY(), EPSILON);
            assertEquals(0.0, secondBody.getBoundsInLocal().getMinX(), EPSILON);
            assertEquals(NeuronView.WIDTH, secondBody.getBoundsInLocal().getMaxX(), EPSILON);
            assertEquals(0.0, secondBody.getBoundsInLocal().getMinY(), EPSILON);
            assertEquals(NeuronView.HEIGHT, secondBody.getBoundsInLocal().getMaxY(), EPSILON);
            assertTrue(drawer.isExpanded());

            structure.disposeParameterTooltip();
            first.disposeParameterTooltip();
            second.disposeParameterTooltip();
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

    /** Перевіряє, що підпис лишається горизонтальним і розташовується над повернутим нейроном зі сталим екранним відступом. */
    @Test
    void positionsNameAboveRotatedNeuronWithFixedScreenGapWhenZoomed() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("rotated-name", NeuronType.EXCITATORY, 0);
            neuron.setName("Rotated label");
            NeuronView view = new NeuronView(new NeuronPresentation(neuron, 40.0, 50.0, 67.0));
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
                    view.localToScene(0.0, 0.0), view.localToScene(NeuronView.WIDTH, 0.0),
                    view.localToScene(0.0, NeuronView.HEIGHT), view.localToScene(NeuronView.WIDTH, NeuronView.HEIGHT)
            };
            double topY = java.util.Arrays.stream(corners).mapToDouble(Point2D::getY).min().orElseThrow();
            Label label = view.nameLabelForTest();
            Bounds labelBounds = label.localToScene(label.getBoundsInLocal());
            double neuronCenterX = view.localToScene(NeuronView.WIDTH / 2.0, NeuronView.HEIGHT / 2.0).getX();

            assertTrue(view.isNameLabelVisibleForTest());
            assertEquals(topY - NeuronOverlayPositioner.MENU_GAP, labelBounds.getMaxY(), 1.0);
            assertEquals(neuronCenterX, labelBounds.getMinX() + labelBounds.getWidth() / 2.0, 1.0);
            assertEquals(0.0, label.getRotate() + view.getRotate(), EPSILON);
            assertEquals(1.0 / 1.75, label.getScaleX(), 0.01);
            assertEquals(1.0 / 1.75, label.getScaleY(), 0.01);

            // Імітує callback оновлення екранних накладок після zoom без руху миші.
            cameraScale.setX(2.25);
            cameraScale.setY(2.25);
            view.refreshHoverNameGeometry();
            root.applyCss();
            root.layout();

            Bounds zoomedLabelBounds = label.localToScene(label.getBoundsInLocal());
            Point2D zoomedNeuronCenter = view.localToScene(NeuronView.WIDTH / 2.0, NeuronView.HEIGHT / 2.0);
            Point2D[] zoomedCorners = {
                    view.localToScene(0.0, 0.0), view.localToScene(NeuronView.WIDTH, 0.0),
                    view.localToScene(0.0, NeuronView.HEIGHT), view.localToScene(NeuronView.WIDTH, NeuronView.HEIGHT)
            };
            double zoomedTopY = java.util.Arrays.stream(zoomedCorners).mapToDouble(Point2D::getY).min().orElseThrow();
            assertEquals(1.0 / 2.25, label.getScaleX(), 0.01);
            assertEquals(1.0 / 2.25, label.getScaleY(), 0.01);
            assertEquals(zoomedTopY - NeuronOverlayPositioner.MENU_GAP, zoomedLabelBounds.getMaxY(), 1.0);
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

    /** Перевіряє, що геометрія тіла враховує рамку всередині загального розміру картки. */
    private static void assertBodyRemainsAtLocalOrigin(Rectangle body) {
        assertEquals(0.0, body.getLayoutX(), EPSILON, "Контур не повинен змінювати горизонтальне розташування тіла");
        assertEquals(0.0, body.getLayoutY(), EPSILON, "Контур не повинен змінювати вертикальне розташування тіла");
        assertEquals(NeuronView.SELECTED_STROKE_WIDTH / 2.0, body.getX(), EPSILON,
                "Тіло має бути трохи зміщене всередину, щоб центрована рамка не виходила за межі картки");
        assertEquals(NeuronView.SELECTED_STROKE_WIDTH / 2.0, body.getY(), EPSILON,
                "Тіло має бути трохи зміщене всередину, щоб центрована рамка не виходила за межі картки");
    }

    /** Створює нейронний вузол заданого типу для перевірки таблиці стилів. */
    private static NeuronView createView(String id, NeuronType type) {
        Neuron neuron = new Neuron(id, type, 0);
        return new NeuronView(new NeuronPresentation(neuron, 40.0, 50.0, 0.0));
    }

    /** Створює синтетичну подію миші для перевірки реакції подання без відображення окремого вікна. */
    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> eventType) {
        return new MouseEvent(
                eventType, 0.0, 0.0, 0.0, 0.0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }
}
