package com.example.neuronmap.persistence;

import com.example.neuronmap.model.Connection;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Читає з SQLite нейрони, їхні представлення, звʼязки та інші дані карти.
 */
public final class SqliteMapLoader {

    private final java.sql.Connection connection;

    /**
     * Повертає результат операції «SQLite карта».
     *
     * @param connection звʼязок між нейронами.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public SqliteMapLoader(java.sql.Connection connection) {
        if (connection == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param model модель карти нейронів.
     */
    public void loadInto(NeuronMapModel model) throws SQLException {
        model.clear();
        loadNeurons(model);
        loadPresentations(model);
        loadConnections(model);
        loadGroups(model);
    }

    /**
     * Повертає або знаходить дані, повʼязані з «нейрони».
     *
     * @param model модель карти нейронів.
     */
    private void loadNeurons(NeuronMapModel model) throws SQLException {
        String sql = """
                SELECT
                    id,
                    type,
                    activation,
                    signal_strength,
                    activation_threshold
                FROM neurons
                ORDER BY rowid
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                model.addNeuron(
                        id,
                        NeuronType.valueOf(resultSet.getString("type")),
                        resultSet.getInt("activation")
                );

                Neuron neuron = model.neuron(id);
                if (neuron == null) {
                    continue;
                }

                neuron.setSignalStrength(
                        resultSet.getInt("signal_strength")
                );
                neuron.setActivationThreshold(
                        resultSet.getInt("activation_threshold")
                );
            }
        }
    }

    /**
     * Повертає або знаходить дані, повʼязані з «відповідну операцію».
     *
     * @param model модель карти нейронів.
     */
    private void loadPresentations(NeuronMapModel model)
            throws SQLException {
        String sql = """
                SELECT
                    neuron_id,
                    x,
                    y,
                    rotation,
                    direction_reversed
                FROM neuron_presentations
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                NeuronPresentation presentation = model.presentation(
                        resultSet.getString("neuron_id")
                );
                if (presentation == null) {
                    continue;
                }

                presentation.setPosition(
                        resultSet.getDouble("x"),
                        resultSet.getDouble("y")
                );
                presentation.setRotationDegrees(
                        resultSet.getDouble("rotation")
                );
                presentation.setDirectionReversed(
                        resultSet.getInt("direction_reversed") != 0
                );
            }
        }
    }

    /**
     * Повертає або знаходить дані, повʼязані з «звʼязки».
     *
     * @param model модель карти нейронів.
     */
    private void loadConnections(NeuronMapModel model)
            throws SQLException {
        String sql = """
                SELECT id, source_id, target_id
                FROM connections
                ORDER BY rowid
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                model.addConnection(
                        resultSet.getString("id"),
                        resultSet.getString("source_id"),
                        resultSet.getString("target_id")
                );
            }
        }
    }

    /**
     * Повертає або знаходить дані, повʼязані з «групи».
     *
     * @param model модель карти нейронів.
     */
    private void loadGroups(NeuronMapModel model) throws SQLException {
        String sql = """
                SELECT group_id, neuron_id
                FROM group_members
                ORDER BY group_id, rowid
                """;

        Map<String, List<String>> membersByGroup =
                new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                membersByGroup
                        .computeIfAbsent(
                                resultSet.getString("group_id"),
                                ignored -> new ArrayList<>()
                        )
                        .add(resultSet.getString("neuron_id"));
            }
        }

        membersByGroup.forEach(model::addGroup);
    }
}

