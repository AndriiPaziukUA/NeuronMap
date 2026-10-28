package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє контракт зберігання даних нейрона, параметри сигналу, назву та валідацію значень. */
class NeuronTest {

    /**
     * Перевіряє, що нейрон зберігає власні семантичні дані, а назва й параметри містяться у спільному об'єкті налаштувань.
     */
    @Test
    void storesOnlySemanticDataAndGroupsSignalParameters() {
        Set<String> neuronFields = instanceFieldNames(Neuron.class);
        Set<String> settingsFields = instanceFieldNames(NeuronSettings.class);

        assertEquals(Set.of("id", "type", "activation", "settings"), neuronFields);
        assertEquals(Set.of("name", "parameters"), settingsFields);
    }

    /**
     * Перевіряє розрахунок ваги сигналу з урахуванням типу нейрона та його сили сигналу.
     */
    @Test
    void signalWeightUsesTypeAndCustomStrength() {
        Neuron excitatory = new Neuron(
                "e",
                NeuronType.EXCITATORY,
                0,
                5,
                3
        );
        Neuron inhibitory = new Neuron(
                "i",
                NeuronType.INHIBITORY,
                0,
                7,
                4
        );

        assertEquals(5, excitatory.signalWeight());
        assertEquals(-7, inhibitory.signalWeight());
        assertEquals(5, excitatory.signalStrength());
        assertEquals(3, excitatory.activationThreshold());
        assertEquals(new NeuronParameters(5, 3), excitatory.parameters());
    }

    /**
     * Перевіряє зміну сили сигналу й порога активації без втрати іншого параметра.
     */
    @Test
    void allowsUpdatingSignalStrengthAndThreshold() {
        Neuron neuron = new Neuron("n1", NeuronType.EXCITATORY, 0);

        neuron.setSignalStrength(9);
        assertEquals(new NeuronParameters(9, 1), neuron.parameters());
        neuron.setActivationThreshold(6);

        assertEquals(9, neuron.signalStrength());
        assertEquals(6, neuron.activationThreshold());
        assertEquals(new NeuronParameters(9, 6), neuron.parameters());
        assertEquals(9, neuron.signalWeight());
    }

    /**
     * Перевіряє відхилення некоректних параметрів конструктора та сеттерів нейрона.
     */
    @Test
    void rejectsInvalidConstructionAndParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new Neuron("", NeuronType.EXCITATORY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Neuron("n1", null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Neuron("n1", NeuronType.EXCITATORY, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Neuron("n1", NeuronType.EXCITATORY, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Neuron("n1", NeuronType.EXCITATORY, 0, null));
    }

    /** Перевіряє, що за замовчуванням назви немає, а порожній рядок очищує її до null. */
    @Test
    void defaultsToNullNameAndClearsEmptyName() {
        Neuron neuron = new Neuron("unnamed", NeuronType.EXCITATORY, 0);

        assertNull(neuron.name());
        neuron.setName("Ім'я");
        neuron.setName("");
        assertNull(neuron.name());
        neuron.setName(null);
        assertNull(neuron.name());
    }

    /** Перевіряє довільні символи та обмеження у 30 кодових точок Unicode. */
    @Test
    void acceptsArbitraryNameCharactersWithinUnicodeLengthLimit() {
        Neuron neuron = new Neuron("named", NeuronType.EXCITATORY, 0);
        String name = "😀".repeat(30);

        neuron.setName(name);
        assertEquals(name, neuron.name());
        neuron.setName("  <Вхід> & Output?  ");
        assertEquals("  <Вхід> & Output?  ", neuron.name());
        assertThrows(IllegalArgumentException.class, () -> neuron.setName("x".repeat(31)));
        assertThrows(IllegalArgumentException.class, () -> neuron.setName("🚀".repeat(31)));
    }

    /** Повертає назви нестатичних полів класу для архітектурної перевірки його стану. */
    private static Set<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
