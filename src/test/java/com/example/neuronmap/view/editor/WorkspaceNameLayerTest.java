package com.example.neuronmap.view.editor;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє порядок екранного шару назв і графічних шарів робочої області. */
final class WorkspaceNameLayerTest {

    /** Переконується, що шухлядки лежать під зв'язками, а назви й накладки — над робочою областю. */
    @Test
    void namesAndStructureDrawerKeepTheirIntendedLayerOrder() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            try {
                Pane world = (Pane) workspace.nodeLayer().getParent();
                List<Node> worldLayers = List.copyOf(world.getChildren());
                assertTrue(worldLayers.indexOf(workspace.structureDrawerLayer())
                                < worldLayers.indexOf(workspace.edgeLayer()),
                        "Фон і контур шухлядки мають бути під зв'язками");
                assertTrue(worldLayers.indexOf(workspace.edgeLayer())
                                < worldLayers.indexOf(workspace.nodeLayer()),
                        "Нейрони мають малюватися над зв'язками");

                Node worldNode = workspace.nodeLayer().getParent();
                var layers = workspace.node().getChildren();
                assertTrue(layers.indexOf(worldNode) < layers.indexOf(workspace.nameLayer()));
                assertTrue(layers.indexOf(workspace.nameLayer()) < layers.indexOf(workspace.overlayLayer()));

                Neuron neuron = new Neuron("overlay-test", NeuronType.EXCITATORY, 0);
                NeuronView view = new NeuronView(new NeuronPresentation(neuron, 50.0, 60.0, 0.0));
                workspace.nodeLayer().getChildren().add(view);
                workspace.nameLayer().getChildren().add(view.nameOverlayNode());

                assertSame(workspace.nameLayer(), view.nameOverlayNode().getParent());
                assertEquals(0, workspace.overlayLayer().getChildren().size(),
                        "Назви не повинні змінювати склад шару ручок та контекстних меню");
                view.disposeParameterTooltip();
            } finally {
                workspace.dispose();
            }
        });
    }
}
