package com.example.neuronmap.view.editor;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.neuron.NeuronView;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє порядок екранного шару назв відносно нейронів, зв'язків та керівних накладок. */
final class WorkspaceNameLayerTest {

    @Test
    void namesStayAboveWorldWithoutChangingInteractionOverlayChildren() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            try {
                Node world = workspace.nodeLayer().getParent();
                var layers = workspace.node().getChildren();
                assertTrue(layers.indexOf(world) < layers.indexOf(workspace.nameLayer()));
                assertTrue(layers.indexOf(workspace.nameLayer()) < layers.indexOf(workspace.overlayLayer()));

                Neuron neuron = new Neuron("overlay-test", NeuronType.EXCITATORY, 0);
                NeuronView view = new NeuronView(new NeuronPresentation(neuron, 50.0, 60.0, 0.0));
                workspace.nodeLayer().getChildren().add(view);
                workspace.nameLayer().getChildren().add(view.nameOverlayNode());

                assertSame(workspace.nameLayer(), view.nameOverlayNode().getParent());
                assertEquals(0, workspace.overlayLayer().getChildren().size(),
                        "Назви не повинні змінювати склад шару ручок та контекстних меню");
            } finally {
                workspace.dispose();
            }
        });
    }
}
