package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє колір і взаємодію попереднього перегляду структурного нейрона. */
final class NeuronDragPreviewViewTest {

    /** Перевіряє, що прев’ю структури має оранжеве тіло та не перехоплює події миші. */
    @Test
    void structurePreviewUsesStructureColorAndDoesNotInterceptMouseEvents() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronDragPreviewView preview = NeuronDragPreviewFactory.create(NeuronType.STRUCTURE);
            Rectangle body = (Rectangle) preview.getChildren().getFirst();

            assertEquals(Color.web("#f59e0b"), body.getFill());
            assertEquals(0.45, preview.getOpacity(), 0.001);
            assertTrue(preview.isMouseTransparent());
            assertTrue(body.isMouseTransparent());
        });
    }
}
