package com.example.neuronmap.view.neuron;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє негайне відображення залишку активних тактів замість сили вихідного сигналу. */
final class NeuronActiveTicksDisplayTest {

    /**
     * Перевіряє миттєву зміну числа, збереження після оновлення вигляду та початкове нульове значення.
     *
     * @throws Exception якщо синхронне виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void remainingTicksUpdateImmediatelyAndSurviveVisualRefresh() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("active-ticks-display-test", NeuronType.EXCITATORY, 0, 7, 1);
            NeuronView view = new NeuronView(new NeuronPresentation(neuron, 100.0, 80.0, 0.0));
            Label remainingTicksLabel = (Label) view.getChildren().get(2);

            assertEquals("0", remainingTicksLabel.getText());
            view.setRemainingActiveTicks(5);
            assertEquals("5", remainingTicksLabel.getText());

            view.refreshVisuals(false);
            assertEquals("5", remainingTicksLabel.getText());

            view.setRemainingActiveTicks(2);
            assertEquals("2", remainingTicksLabel.getText());
        });
    }

    /**
     * Перевіряє відхилення від'ємного залишку, який не може виникнути у коректному такті симуляції.
     *
     * @throws Exception якщо синхронне виконання дії в JavaFX-потоці завершується помилкою.
     */
    @Test
    void rejectsNegativeRemainingTicks() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("active-ticks-negative-test", NeuronType.EXCITATORY, 0);
            NeuronView view = new NeuronView(new NeuronPresentation(neuron, 100.0, 80.0, 0.0));

            assertThrows(IllegalArgumentException.class, () -> view.setRemainingActiveTicks(-1));
            assertEquals("0", ((Label) view.getChildren().get(2)).getText());
        });
    }
}
