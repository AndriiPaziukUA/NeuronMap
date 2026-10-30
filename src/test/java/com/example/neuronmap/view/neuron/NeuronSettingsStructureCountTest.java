package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє окремі поля структури, їхню валідацію та відсутність у налаштуваннях звичайного нейрона. */
final class NeuronSettingsStructureCountTest {

    /** Перевіряє завантаження поточної кількості, мінімум і передавання збільшеної цілі. */
    @Test
    void loadsCurrentCountAndOnlyAcceptsNonDecreasingTarget() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.forLanguageTag("uk")));
            StackPane root = new StackPane();
            new Scene(root, 1000.0, 800.0);
            Neuron structure = neuron("settings-structure", NeuronType.STRUCTURE);
            AtomicInteger savedCount = new AtomicInteger(-1);

            view.showWithStructureCountIn(root, structure, 3, (_, _, count) -> savedCount.set(count));
            assertEquals("3", view.structureMemberCountFieldForTest().getText());
            assertEquals("Кількість нейронів:",
                    view.settingLabelTextForTest("i18n.neuronSettings.structure_neuron_count"));
            assertTrue(view.structureMemberCountRowForTest().isVisible());
            assertTrue(view.structureMemberCountRowForTest().isManaged());

            view.structureMemberCountFieldForTest().setText("2");
            assertTrue(view.saveButtonForTest().isDisabled());
            view.saveButtonForTest().fire();
            assertEquals(-1, savedCount.get(), "Некоректне значення не повинно передаватися callback");

            view.structureMemberCountFieldForTest().setText("4");
            assertFalse(view.saveButtonForTest().isDisabled());
            view.saveButtonForTest().fire();
            assertEquals(4, savedCount.get());

            view.showWithStructureCountIn(root, structure, 2, (_, _, count) -> savedCount.set(count));
            assertEquals("2", view.structureMemberCountFieldForTest().getText(),
                    "Повторне відкриття має показувати поточний стан структури, а не попереднє введення");
            view.structureMemberCountFieldForTest().setText("1");
            assertTrue(view.saveButtonForTest().isDisabled(),
                    "Після повторного відкриття мінімум має відповідати новій поточній кількості");
            view.hide();
            view.dispose();
        });
    }

    /** Перевіряє, що поле кількості й перемикач відсутні для звичайного нейрона. */
    @Test
    void restrictsInputToDigitsAndHidesStructureFieldsForOrdinaryNeuron() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            StackPane root = new StackPane();
            new Scene(root, 1000.0, 800.0);
            AtomicInteger savedCount = new AtomicInteger(Integer.MIN_VALUE);

            view.showWithStructureCountIn(root, neuron("ordinary-neuron", NeuronType.EXCITATORY), -1,
                    (_, _, count) -> savedCount.set(count));
            assertFalse(view.structureMemberCountRowForTest().isVisible());
            assertFalse(view.structureMemberCountRowForTest().isManaged());
            assertFalse(view.strongestNeuronOnlyRowForTest().isVisible());
            assertFalse(view.strongestNeuronOnlyRowForTest().isManaged());
            view.structureMemberCountFieldForTest().setText("12");
            view.structureMemberCountFieldForTest().replaceText(0, 2, "-1");
            assertEquals("12", view.structureMemberCountFieldForTest().getText());
            view.saveButtonForTest().fire();
            assertEquals(-1, savedCount.get());
            view.dispose();
        });
    }

    /** Перевіряє, що нульову кількість дозволено для порожньої структури. */
    @Test
    void acceptsZeroForEmptyStructure() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            StackPane root = new StackPane();
            new Scene(root, 1000.0, 800.0);
            AtomicInteger savedCount = new AtomicInteger(-1);

            view.showWithStructureCountIn(root, neuron("empty-structure", NeuronType.STRUCTURE), 0,
                    (_, _, count) -> savedCount.set(count));
            assertEquals("0", view.structureMemberCountFieldForTest().getText());
            assertFalse(view.saveButtonForTest().isDisabled());
            view.saveButtonForTest().fire();
            assertEquals(0, savedCount.get());
            view.dispose();
        });
    }

    /** Перевіряє порядок, початкове значення, локалізований підпис і збереження перемикача структури. */
    @Test
    void strongestNeuronControlIsStructureOnlyAndSavesSelectedValue() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.forLanguageTag("uk")));
            StackPane root = new StackPane();
            new Scene(root, 1000.0, 800.0);
            NeuronMapModel model = new NeuronMapModel();
            Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
            model.structure(structure.id()).settings().setStrongestNeuronOnly(true);
            AtomicBoolean savedFlag = new AtomicBoolean(false);

            view.showWithStructureSettingsIn(root, structure, 1,
                    (_, _, _, strongest) -> savedFlag.set(strongest));

            assertTrue(view.strongestNeuronOnlyRowForTest().isVisible());
            assertTrue(view.strongestNeuronOnlyRowForTest().isManaged());
            assertTrue(view.strongestNeuronOnlyToggleForTest().isSelected());
            assertEquals("Найсильніший нейрон:",
                    view.settingLabelTextForTest("i18n.neuronSettings.strongest_neuron"));
            var rows = ((javafx.scene.layout.VBox) view.strongestNeuronOnlyRowForTest().getParent()).getChildren();
            assertEquals(rows.indexOf(view.structureMemberCountRowForTest()) + 1,
                    rows.indexOf(view.strongestNeuronOnlyRowForTest()));

            view.strongestNeuronOnlyToggleForTest().fire();
            assertFalse(view.strongestNeuronOnlyToggleForTest().isSelected());
            view.saveButtonForTest().fire();
            assertFalse(savedFlag.get());
            view.dispose();
        });
    }

    /** Створює нейрон з параметрами, які не перешкоджають перевірці форми налаштувань. */
    private static Neuron neuron(String id, NeuronType type) {
        return new Neuron(id, type, 0, new NeuronParameters(7, 3, 5));
    }
}
