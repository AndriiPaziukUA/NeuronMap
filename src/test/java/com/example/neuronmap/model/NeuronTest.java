package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NeuronTest {

    @Test
    void storesOnlySemanticData() {
        Set<String> instanceFields =
                Arrays.stream(
                        Neuron.class.getDeclaredFields()
                )
                .filter(field ->
                        !Modifier.isStatic(
                                field.getModifiers()
                        )
                )
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "id",
                        "type",
                        "activation",
                        "signalStrength",
                        "activationThreshold"
                ),
                instanceFields
        );
    }

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
    }

    @Test
    void allowsUpdatingSignalStrengthAndThreshold() {
        Neuron neuron = new Neuron(
                "n1",
                NeuronType.EXCITATORY,
                0
        );

        neuron.setSignalStrength(9);
        neuron.setActivationThreshold(6);

        assertEquals(9, neuron.signalStrength());
        assertEquals(6, neuron.activationThreshold());
        assertEquals(9, neuron.signalWeight());
    }

    @Test
    void rejectsInvalidConstructionAndParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Neuron(
                        "",
                        NeuronType.EXCITATORY,
                        0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Neuron(
                        "n1",
                        null,
                        0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Neuron(
                        "n1",
                        NeuronType.EXCITATORY,
                        0,
                        0,
                        1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Neuron(
                        "n1",
                        NeuronType.EXCITATORY,
                        0,
                        1,
                        0
                )
        );
    }
}
