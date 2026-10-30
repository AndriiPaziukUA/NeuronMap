package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє склад підказки структури залежно від відкритості шухлядки та параметри її учасників. */
final class NeuronStructureCountTooltipTest {

    /** Перевіряє секції закритої структури, стан найсильнішого нейрона й актуальність лічильника. */
    @Test
    void showsStructureAndNeuronSectionsWhenDrawerIsClosed() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0, 0.0);
            structure.setParameters(new NeuronParameters(7, 3, 5));
            model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
            NeuronView structureView = new NeuronView(
                    new NeuronPresentation(structure, 10.0, 20.0, 0.0));
            NeuronView firstMember = view("tooltip-member-1", NeuronType.EXCITATORY);
            NeuronView secondMember = view("tooltip-member-2", NeuronType.INHIBITORY);
            structureView.setStructureMembers(List.of(firstMember, secondMember));
            structureView.configureParameterTooltipLocalization(localization);

            String expectedTooltip = """
                    Параметри
                    Структура
                    Кількість нейронів: 2
                    Найсильніший нейрон: Вкл
                    Нейрони
                    Сила сигналу: 7
                    Поріг активації: 3
                    Активні такти: 5
                    Затримка: 1""";
            assertEquals(expectedTooltip, structureView.parameterTooltipTextForTest());
            List<Node> tooltipNodes = structureView.parameterTooltipContentForTest().getChildren();
            assertTrue(tooltipNodes.get(1).isVisible(),
                    "Під головним заголовком має бути роздільник, який одночасно відокремлює наступний підзаголовок");
            assertEquals(0.5, ((Region) tooltipNodes.get(1)).getMinHeight(),
                    "Роздільник має бути тоншим за попередню лінію заввишки один піксель");
            assertEquals("Структура", ((Label) tooltipNodes.get(2)).getText());
            assertEquals(Pos.CENTER, ((Label) tooltipNodes.get(2)).getAlignment(),
                    "Підзаголовок структури має бути відцентрований");
            assertTrue(tooltipNodes.get(3).isVisible(), "Під заголовком структури має бути тонкий роздільник");
            assertEquals(0.5, ((Region) tooltipNodes.get(3)).getMinHeight());
            assertTrue(tooltipNodes.get(6).isVisible(), "Над заголовком нейронів має бути тонкий роздільник");
            assertEquals("Нейрони", ((Label) tooltipNodes.get(7)).getText());
            assertEquals(Pos.CENTER, ((Label) tooltipNodes.get(7)).getAlignment(),
                    "Підзаголовок нейронів має бути відцентрований");
            assertTrue(tooltipNodes.get(8).isVisible(), "Під заголовком нейронів має бути тонкий роздільник");
            assertEquals(0.5, ((Region) tooltipNodes.get(8)).getMinHeight());

            structureView.setStructureMembers(List.of(firstMember));
            assertTrue(structureView.parameterTooltipTextForTest().contains("Кількість нейронів: 1"));

            structureView.disposeParameterTooltip();
            firstMember.disposeParameterTooltip();
            secondMember.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що відкрита структура показує лише власні параметри, а учасник — параметри нейрона. */
    @Test
    void showsOnlyStructureParametersWhenDrawerIsOpenAndKeepsMemberTooltip() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0, 0.0);
            structure.setParameters(new NeuronParameters(7, 3, 5));
            model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
            NeuronView structureView = new NeuronView(
                    new NeuronPresentation(structure, 10.0, 20.0, 0.0));
            NeuronView member = view("tooltip-visible-member", NeuronType.EXCITATORY);
            structureView.setStructureMembers(List.of(member));
            structureView.configureParameterTooltipLocalization(localization);
            member.configureParameterTooltipLocalization(localization);

            assertTrue(structureView.toggleStructureDrawer());
            assertEquals("Параметри\nКількість нейронів: 1\nНайсильніший нейрон: Вкл",
                    structureView.parameterTooltipTextForTest());
            List<Node> tooltipNodes = structureView.parameterTooltipContentForTest().getChildren();
            assertTrue(tooltipNodes.get(1).isVisible(),
                    "Роздільник під головним заголовком має залишатися видимим у відкритій структурі");
            assertFalse(tooltipNodes.get(2).isVisible());
            assertFalse(tooltipNodes.get(3).isVisible());
            assertTrue(tooltipNodes.get(4).isVisible());
            assertTrue(tooltipNodes.get(5).isVisible());
            assertFalse(tooltipNodes.get(6).isVisible());
            assertFalse(tooltipNodes.get(7).isVisible());
            assertFalse(tooltipNodes.get(8).isVisible());
            assertEquals("Параметри\nСила сигналу: 7\nПоріг активації: 3\nАктивні такти: 5\nЗатримка: 1",
                    member.parameterTooltipTextForTest());

            model.structure(structure.id()).settings().setStrongestNeuronOnly(false);
            structureView.refreshVisuals(false);
            assertEquals("Параметри\nКількість нейронів: 1\nНайсильніший нейрон: Викл",
                    structureView.parameterTooltipTextForTest());

            assertFalse(structureView.toggleStructureDrawer());
            assertTrue(structureView.parameterTooltipTextForTest().contains("Найсильніший нейрон: Викл"));
            assertTrue(structureView.parameterTooltipTextForTest().contains("Нейрони\nСила сигналу: 7"));

            structureView.disposeParameterTooltip();
            member.disposeParameterTooltip();
        });
    }

    /** Перевіряє заголовок і відсутність параметрів структури у звичайного нейрона. */
    @Test
    void showsNeuronParametersWithoutStructureFieldsForOrdinaryNeuron() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView ordinaryView = view("tooltip-ordinary", NeuronType.EXCITATORY);
            ordinaryView.configureParameterTooltipLocalization(
                    new LocalizationService(Locale.forLanguageTag("uk")));

            assertFalse(ordinaryView.parameterTooltipTextForTest().contains("Кількість нейронів"));
            assertFalse(ordinaryView.parameterTooltipTextForTest().contains("Найсильніший нейрон"));
            assertEquals("Параметри\nСила сигналу: 7\nПоріг активації: 3\nАктивні такти: 5\nЗатримка: 1",
                    ordinaryView.parameterTooltipTextForTest());
            ordinaryView.disposeParameterTooltip();
        });
    }

    /** Створює подання тестового нейрона з фіксованими параметрами та координатами. */
    private static NeuronView view(String id, NeuronType type) {
        Neuron neuron = new Neuron(id, type, 0, new NeuronParameters(7, 3, 5));
        return new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
    }
}
