package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що кількість додається на початок підказки структури, але не звичайного нейрона. */
final class NeuronStructureCountTooltipTest {

    /** Перевіряє початковий рядок лічильника та його оновлення після зміни складу шухлядки. */
    @Test
    void showsLiveMemberCountOnlyOnStructureTooltip() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            NeuronView structureView = view("tooltip-structure", NeuronType.STRUCTURE);
            NeuronView firstMember = view("tooltip-member-1", NeuronType.EXCITATORY);
            NeuronView secondMember = view("tooltip-member-2", NeuronType.INHIBITORY);
            structureView.setStructureMembers(List.of(firstMember, secondMember));
            structureView.configureParameterTooltipLocalization(localization);

            assertTrue(structureView.parameterTooltipTextForTest().startsWith("Кількість нейронів: 2\n"));
            structureView.setStructureMembers(List.of(firstMember));
            assertTrue(structureView.parameterTooltipTextForTest().startsWith("Кількість нейронів: 1\n"));

            NeuronView ordinaryView = view("tooltip-ordinary", NeuronType.EXCITATORY);
            ordinaryView.configureParameterTooltipLocalization(localization);
            assertFalse(ordinaryView.parameterTooltipTextForTest().contains("Кількість нейронів"));
            assertEquals("Сила сигналу: 7\nПоріг активації: 3\nАктивні такти: 5\nЗатримка: 1",
                    ordinaryView.parameterTooltipTextForTest());

            structureView.disposeParameterTooltip();
            firstMember.disposeParameterTooltip();
            secondMember.disposeParameterTooltip();
            ordinaryView.disposeParameterTooltip();
        });
    }

    /** Створює подання тестового нейрона з фіксованими параметрами та координатами. */
    private static NeuronView view(String id, NeuronType type) {
        Neuron neuron = new Neuron(id, type, 0, new NeuronParameters(7, 3, 5));
        return new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
    }
}
