package com.example.neuronmap.view.connection;

import com.example.neuronmap.model.NeuronType;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє палітру, вибір кольорів за типом джерела й валідацію стилю зв'язків. */
final class ConnectionStyleTest {

    /** Перевіряє базові кольори лінії, наконечника та імпульсу для всіх типів нейронів. */
    @Test
    void selectsBaseAndPulseColorsByNeuronType() {
        ConnectionStyle style = ConnectionStyle.DEFAULT;

        assertEquals(Color.web("#55bf78"), style.sourceColor(NeuronType.EXCITATORY));
        assertEquals(Color.web("#8b929c"), style.sourceColor(NeuronType.INHIBITORY));
        assertEquals(Color.web("#8b929c"), style.sourceColor(null));
        assertEquals(Color.web("#f59e0b"), style.sourceColor(NeuronType.STRUCTURE));
        assertEquals(Color.web("#f59e0b"), style.arrowColor(NeuronType.STRUCTURE));
        assertEquals(Color.web("#55bf78"), style.arrowColor(NeuronType.EXCITATORY));
        assertEquals(Color.web("#facc15"), style.pulseColor(NeuronType.EXCITATORY));
        assertEquals(Color.web("#ef4444"), style.pulseColor(NeuronType.INHIBITORY));
        assertEquals(Color.web("#38bdf8"), style.pulseColor(NeuronType.STRUCTURE));
    }

    /** Перевіряє, що кольори рухомих імпульсів можна налаштовувати незалежно від основних кольорів зв'язків. */
    @Test
    void configuresPulseColorsIndependentlyFromConnectionColors() {
        ConnectionStyle style = new ConnectionStyle(
                Color.GREEN,
                Color.GRAY,
                Color.BLACK,
                Color.YELLOW,
                Color.RED,
                Color.ORANGE,
                2.3,
                18.0,
                11.0,
                5.5
        );

        assertEquals(Color.GREEN, style.sourceColor(NeuronType.EXCITATORY));
        assertEquals(Color.YELLOW, style.pulseColor(NeuronType.EXCITATORY));
        assertEquals(Color.GRAY, style.sourceColor(NeuronType.INHIBITORY));
        assertEquals(Color.RED, style.pulseColor(NeuronType.INHIBITORY));
        assertEquals(Color.web("#f59e0b"), style.sourceColor(NeuronType.STRUCTURE));
        assertEquals(Color.web("#f59e0b"), style.arrowColor(NeuronType.STRUCTURE));
    }

    /** Відхиляє неприпустиму товщину лінії, не створюючи частково валідний стиль. */
    @Test
    void rejectsInvalidGeometryParameters() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionStyle(
                Color.GREEN, Color.GRAY, Color.GRAY, Color.LIGHTGREEN, Color.PINK, Color.YELLOW,
                0.0, 18.0, 11.0, 5.5));
    }

    /** Вимагає наявності всіх кольорів. */
    @Test
    void requiresAllStyleColors() {
        assertThrows(NullPointerException.class, () -> new ConnectionStyle(
                null, Color.GRAY, Color.GRAY, Color.LIGHTGREEN, Color.PINK, Color.YELLOW,
                2.3, 18.0, 11.0, 5.5));
    }
}
