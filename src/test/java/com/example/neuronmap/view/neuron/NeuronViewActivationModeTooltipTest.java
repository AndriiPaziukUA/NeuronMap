package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що підказка позначає поріг відсотком лише для режиму активації за кількістю сигналів. */
final class NeuronViewActivationModeTooltipTest {

    /** Перевіряє формат відсоткового порога в локалізованій підказці нейрона. */
    @Test
    void displaysPercentSignForSignalCountMode() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("percentage-tooltip", NeuronType.EXCITATORY, 0,
                    new NeuronParameters(7, 33, 5, 1, NeuronActivationMode.SIGNAL_COUNT));
            NeuronView view = new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.forLanguageTag("uk")));

            assertTrue(view.parameterTooltipTextForTest().contains("Поріг активації: 33%"));
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що в режимі сили сигналів поріг залишається без символу відсотка. */
    @Test
    void omitsPercentSignForSignalStrengthMode() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Neuron neuron = new Neuron("strength-tooltip", NeuronType.EXCITATORY, 0, 7, 33);
            NeuronView view = new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.forLanguageTag("uk")));

            String tooltip = view.parameterTooltipTextForTest();
            assertTrue(tooltip.contains("Поріг активації: 33"));
            assertFalse(tooltip.contains("Поріг активації: 33%"));
            view.disposeParameterTooltip();
        });
    }
}
