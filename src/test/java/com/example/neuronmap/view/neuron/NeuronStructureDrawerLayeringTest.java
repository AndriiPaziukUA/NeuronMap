package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє чергу малювання поверхні шухлядки, зв'язків, вкладених нейронів і сяйва структури. */
final class NeuronStructureDrawerLayeringTest {

    private static final double EPSILON = 0.001;

    /** Поверхня має бути під зв'язками, а вміст і сяйво — над поверхнею та зв'язками. */
    @Test
    void drawerSurfaceConnectionsMembersAndGlowUseCorrectLayerOrder() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            NeuronView structure = createView("layering-structure", NeuronType.STRUCTURE, 80.0, 60.0);
            NeuronView member = createView("layering-member", NeuronType.EXCITATORY, 100.0, 100.0);
            try {
                structure.setStructureMembers(List.of(member));
                assertTrue(structure.toggleStructureDrawer());
                workspace.nodeLayer().getChildren().add(structure);
                NeuronStructureDrawerSurfaceView surface = new NeuronStructureDrawerSurfaceView();
                workspace.structureDrawerLayer().getChildren().add(surface);

                Scene scene = new Scene(workspace.node(), 500.0, 400.0);
                scene.getStylesheets().add(Objects.requireNonNull(
                        NeuronStructureDrawerLayeringTest.class.getResource("/app.css"),
                        "Не знайдено основну таблицю стилів").toExternalForm());
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                surface.refreshGeometry(structure, workspace.structureDrawerLayer());

                assertTrue(surface.isVisible(), "Відкрита шухлядка повинна мати видиму поверхню");
                assertTrue(surface.isMouseTransparent(), "Поверхня не повинна перехоплювати події миші");
                assertEquals(Color.web("#242830"), surface.getFill(), "Тло шухлядки має залишатися непрозорим");
                assertEquals(Color.web("#25292f"), surface.getStroke(), "Контур шухлядки має залишатися видимим");
                assertSame(workspace.structureDrawerLayer(), surface.getParent());
                MoveTo surfaceOrigin = (MoveTo) surface.getElements().getFirst();
                assertEquals(80.0, surfaceOrigin.getX(), EPSILON);
                assertEquals(60.0 + NeuronView.HEIGHT, surfaceOrigin.getY(), EPSILON);
                assertInstanceOf(ClosePath.class, surface.getElements().getLast(),
                        "Контур має замикатися, щоб малювалося суцільне тло");

                Pane world = (Pane) workspace.nodeLayer().getParent();
                List<Node> layers = List.copyOf(world.getChildren());
                assertTrue(layers.indexOf(workspace.structureDrawerLayer()) < layers.indexOf(workspace.edgeLayer()),
                        "Шухлядка має малюватися перед зв'язками");
                assertTrue(layers.indexOf(workspace.edgeLayer()) < layers.indexOf(workspace.nodeLayer()),
                        "Нейрони мають малюватися поверх зв'язків");

                NeuronStructureDrawerView drawer = findDrawer(structure);
                Rectangle structureBody = (Rectangle) structure.getChildren().getFirst();
                structure.refreshVisuals(true);
                scene.getRoot().applyCss();
                assertInstanceOf(DropShadow.class, structureBody.getEffect(),
                        "Вибраний структурний нейрон має зберігати сяйво");
                assertTrue(drawer.getViewOrder() > structureBody.getViewOrder(),
                        "Сяйво структури має малюватися поверх учасників шухлядки");
                assertTrue(drawer.memberViews().contains(member));
                assertSame(drawer, member.getParent());

                structure.toggleStructureDrawer();
                surface.refreshGeometry(structure, workspace.structureDrawerLayer());
                assertFalse(surface.isVisible(), "Поверхня закритої шухлядки має ховатися");
            } finally {
                structure.disposeParameterTooltip();
                member.disposeParameterTooltip();
                workspace.dispose();
            }
        });
    }

    /** Створює нейронне подання для тестування шарування без підключення контролерів. */
    private static NeuronView createView(String id, NeuronType type, double x, double y) {
        Neuron neuron = new Neuron(id, type, 0);
        return new NeuronView(new NeuronPresentation(neuron, x, y, 0.0));
    }

    /** Знаходить шухлядку серед графічних дочірніх вузлів структурного нейрона. */
    private static NeuronStructureDrawerView findDrawer(NeuronView structure) {
        return structure.getChildren().stream()
                .filter(NeuronStructureDrawerView.class::isInstance)
                .map(NeuronStructureDrawerView.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
