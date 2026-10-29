package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє окреме поле кількості структури, його валідацію та відсутність у звичайного нейрона. */
final class NeuronSettingsStructureCountTest {

    /** Перевіряє завантаження поточної кількості, мінімальне значення та передачу збільшеної цілі. */
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

    /** Перевіряє, що форматтер відхиляє нецифрове введення й звичайний нейрон не показує поле структури. */
    @Test
    void restrictsInputToDigitsAndHidesFieldForOrdinaryNeuron() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronSettingsView view = new NeuronSettingsView(new LocalizationService(Locale.ENGLISH));
            StackPane root = new StackPane();
            new Scene(root, 1000.0, 800.0);
            AtomicInteger savedCount = new AtomicInteger(Integer.MIN_VALUE);

            view.showWithStructureCountIn(root, neuron("ordinary-neuron", NeuronType.EXCITATORY), -1,
                    (_, _, count) -> savedCount.set(count));
            assertFalse(view.structureMemberCountRowForTest().isVisible());
            assertFalse(view.structureMemberCountRowForTest().isManaged());
            view.structureMemberCountFieldForTest().setText("12");
            view.structureMemberCountFieldForTest().replaceText(0, 2, "-1");
            assertEquals("12", view.structureMemberCountFieldForTest().getText());
            view.saveButtonForTest().fire();
            assertEquals(-1, savedCount.get());
            view.dispose();
        });
    }

    /** Перевіряє, що невід'ємне нульове значення дозволено для порожньої структури. */
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

    /** Створює нейрон з параметрами, які не перешкоджають перевірці форми налаштувань. */
    private static Neuron neuron(String id, NeuronType type) {
        return new Neuron(id, type, 0, new NeuronParameters(7, 3, 5));
    }
}
