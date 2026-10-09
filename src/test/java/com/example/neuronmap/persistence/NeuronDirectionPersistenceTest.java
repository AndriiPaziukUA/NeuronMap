package com.example.neuronmap.persistence;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що напрямок нейрона зберігається та відновлюється.
 */
class NeuronDirectionPersistenceTest {

    /**
     * Перевіряє очікувану поведінку: напрямок база даних.
     *
     * @param tempDir значення, що визначає відповідну операцію для цієї операції.
     */
    @Test
    void reversedDirectionSurvivesDatabaseRoundTrip(
            @TempDir Path tempDir
    ) {
        Path database = tempDir.resolve("direction.db");

        NeuronMapModel savedModel = new NeuronMapModel();
        Neuron neuron = savedModel.createNeuron(
                NeuronType.EXCITATORY,
                100,
                100
        );

        NeuronPresentation savedPresentation =
                savedModel.presentation(neuron.id());
        assertNotNull(savedPresentation);
        savedPresentation.setDirectionReversed(true);

        SqliteMapRepository writer =
                new SqliteMapRepository(database);
        try {
            writer.save(
                    savedModel,
                    CameraState.defaultState()
            );
        } finally {
            writer.close();
        }

        NeuronMapModel loadedModel = new NeuronMapModel();
        SqliteMapRepository reader =
                new SqliteMapRepository(database);
        try {
            reader.loadInto(loadedModel);
        } finally {
            reader.close();
        }

        NeuronPresentation loadedPresentation =
                loadedModel.presentation(neuron.id());

        assertNotNull(loadedPresentation);
        assertTrue(
                loadedPresentation.directionReversed()
        );
    }
}
