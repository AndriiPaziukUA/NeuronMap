package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Зчитує з SQLite нейрони, їхнє подання, зв’язки, групи та структури й відновлює ними модель карти. */
public final class SqliteMapLoader {

    private final java.sql.Connection connection;

    /**
     * Створює завантажувач для відкритого з'єднання SQLite.
     *
     * @param connection відкрите з'єднання з базою даних.
     * @throws IllegalArgumentException якщо з'єднання не задано.
     */
    public SqliteMapLoader(java.sql.Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    /**
     * Відновлює модель карти, послідовно завантажуючи нейрони, подання, зв'язки, групи та структури.
     *
     * @param model модель карти, яку потрібно заповнити.
     * @throws SQLException якщо не вдалося прочитати будь-яку таблицю карти.
     */
    public void loadInto(NeuronMapModel model) throws SQLException {
        model.clear();
        loadNeurons(model);
        loadPresentations(model);
        loadConnections(model);
        loadGroups(model);
        loadStructures(model);
    }

    /** Завантажує всі семантичні параметри нейрона, включно з режимом активації та його назвою. */
    private void loadNeurons(NeuronMapModel model) throws SQLException {
        // SqliteSchema.migrate() забезпечує наявність усіх стовпців параметрів нейрона.
        //noinspection SqlResolve
        String sql = """
                SELECT
                    id,
                    type,
                    activation,
                    signal_strength,
                    activation_threshold,
                    active_ticks,
                    delay_ticks,
                    activation_mode,
                    name
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

                neuron.setParameters(new NeuronParameters(
                        resultSet.getInt("signal_strength"),
                        resultSet.getInt("activation_threshold"),
                        resultSet.getInt("active_ticks"),
                        resultSet.getInt("delay_ticks"),
                        NeuronActivationMode.valueOf(resultSet.getString("activation_mode"))
                ));
                neuron.setName(resultSet.getString("name"));
            }
        }
    }

    /** Завантажує координати, обертання й напрямок візуальних подань нейронів. */
    private void loadPresentations(NeuronMapModel model) throws SQLException {
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
                presentation.setRotationDegrees(resultSet.getDouble("rotation"));
                presentation.setDirectionReversed(resultSet.getInt("direction_reversed") != 0);
            }
        }
    }

    /** Відновлює напрямлені зв'язки між нейронами після завантаження самих нейронів. */
    private void loadConnections(NeuronMapModel model) throws SQLException {
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

    /** Відновлює групи після завантаження нейронів, збираючи учасників за ідентифікатором групи. */
    private void loadGroups(NeuronMapModel model) throws SQLException {
        String sql = """
                SELECT group_id, neuron_id
                FROM group_members
                ORDER BY group_id, rowid
                """;
        Map<String, List<String>> membersByGroup = new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                membersByGroup.computeIfAbsent(
                                resultSet.getString("group_id"),
                                ignored -> new ArrayList<>()
                        )
                        .add(resultSet.getString("neuron_id"));
            }
        }

        membersByGroup.forEach(model::addGroup);
    }

    /**
     * Відновлює окремі записи структур, а потім упорядковане членство кожної структури.
     * Перевіряє, що кожен запис контейнера посилається на нейрон, який вже завантажено до моделі.
     *
     * @param model модель карти, до якої додаються відновлені структури.
     * @throws SQLException якщо читання таблиць не вдалося або запис структури посилається на відсутній нейрон.
     */
    private void loadStructures(NeuronMapModel model) throws SQLException {
        String structureSql = "SELECT id FROM structures ORDER BY rowid";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(structureSql)) {
            while (resultSet.next()) {
                String structureId = resultSet.getString("id");
                if (!model.addStructure(structureId)) {
                    throw new SQLException(
                            "Не вдалося відновити структуру з ідентифікатором '" + structureId
                                    + "': відповідний нейрон відсутній у моделі."
                    );
                }
            }
        }

        String membersSql = """
                SELECT structure_id, neuron_id
                FROM structure_members
                ORDER BY structure_id, position_index
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(membersSql)) {
            while (resultSet.next()) {
                model.addStructureMember(
                        resultSet.getString("structure_id"),
                        resultSet.getString("neuron_id")
                );
            }
        }
    }
}
