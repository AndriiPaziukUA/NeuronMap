package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що значок зміни напрямку є однією стрілкою проти поточної орієнтації нейрона. */
final class NeuronMenuDirectionIconTest {

    /** Перевіряє напрямок стрілки до та після перемикання орієнтації нейрона. */
    @Test
    void directionIconAlwaysPointsAgainstCurrentNeuronDirection() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicBoolean directionReversed = new AtomicBoolean(false);
            NeuronMenuView view = new NeuronMenuView(
                    () -> "direction-icon-test",
                    ignored -> directionReversed.set(!directionReversed.get()),
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );

            Button directionButton = view.createDirectionButton(directionReversed::get);
            assertNotNull(directionButton);
            assertEquals("M16 10H4M8 6l-4 4 4 4", directionPath(directionButton),
                    "Нейрон, спрямований праворуч, має показувати стрілку ліворуч");

            directionButton.fire();

            assertTrue(directionReversed.get());
            assertEquals("M4 10h12M12 6l4 4-4 4", directionPath(directionButton),
                    "Нейрон, спрямований ліворуч, має показувати стрілку праворуч");
        });
    }

    /** Перевіряє, що для структури значок використовує напрямок зі спільних налаштувань її дерева. */
    @Test
    void structureDirectionIconFollowsSharedStructureOrientation() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            NeuronService neuronService = new NeuronService(model);
            Neuron structure = neuronService.create(NeuronType.STRUCTURE, 100.0, 80.0);
            NeuronView structureView = new NeuronView(
                    neuronService.presentation(structure.id()).orElseThrow());
            NeuronMenuView menuView = new NeuronMenuView(
                    structure::id,
                    neuronService::toggleDirection,
                    new LocalizationService(Locale.forLanguageTag("uk"))
            );

            Button directionButton = menuView.createDirectionButton(structureView::isDirectionReversed);
            assertNotNull(directionButton);
            assertEquals("M16 10H4M8 6l-4 4 4 4", directionPath(directionButton));

            directionButton.fire();

            assertTrue(structureView.isDirectionReversed());
            assertEquals("M4 10h12M12 6l4 4-4 4", directionPath(directionButton));
        });
    }

    /**
     * Повертає SVG-шлях стрілки, щоб перевірити фактичний напрямок її геометрії.
     *
     * @param directionButton кнопка, графічний значок якої перевіряється.
     * @return вміст SVG-шляху стрілки.
     */
    private static String directionPath(Button directionButton) {
        Pane icon = assertInstanceOf(Pane.class, directionButton.getGraphic());
        SVGPath arrow = assertInstanceOf(SVGPath.class, icon.getChildren().getFirst());
        assertTrue(arrow.getStyleClass().contains("neuron-menu-icon-stroke"));
        return arrow.getContent();
    }
}
