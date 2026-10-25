package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вміст підказки параметрів нейрона та її поведінку для вибраного елемента. */
final class NeuronViewParameterTooltipTest {

    /** Перевіряє, що підказка показує всі чотири параметри з локалізованими назвами. */
    @Test
    void showsOnlyLocalizedNeuronSettings() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.forLanguageTag("uk")));

            assertEquals(
                    "Сила сигналу: 7\nПоріг активації: 3\nАктивні такти: 5\nЗатримка: 1",
                    view.parameterTooltipTextForTest()
            );
            assertTrue(view.isParameterTooltipInstalledForTest());
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що підказка прибирається під час вибору нейрона й повертається після зняття вибору. */
    @Test
    void hidesTooltipForSelectedNeuronAndRestoresItAfterDeselection() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            view.configureParameterTooltipLocalization(localization);

            view.refreshVisuals(true);
            assertFalse(view.isParameterTooltipInstalledForTest());

            view.refreshVisuals(false);
            assertTrue(view.isParameterTooltipInstalledForTest());
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє актуальність усіх параметрів у підказці після їх зміни в моделі нейрона. */
    @Test
    void refreshesTooltipWhenNeuronParametersChange() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.ENGLISH));
            view.model().setParameters(new NeuronParameters(9, 4, 2, 3));
            view.refreshVisuals(false);

            assertEquals(
                    "Signal strength: 9\nActivation threshold: 4\nActive ticks: 2\nDelay: 3",
                    view.parameterTooltipTextForTest()
            );
            view.disposeParameterTooltip();
        });
    }

    /** Створює подання з контрольованими параметрами для ізольованої перевірки тексту підказки. */
    private static NeuronView createView() {
        Neuron neuron = new Neuron(
                "tooltip-neuron",
                NeuronType.EXCITATORY,
                0,
                new NeuronParameters(7, 3, 5)
        );
        return new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
    }
}
