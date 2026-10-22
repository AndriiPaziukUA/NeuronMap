package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Записує стан моделі карти й параметри камери до таблиць SQLite в межах операції збереження. */
public final class SqliteMapWriter {

    private final java.sql.Connection connection;
    private final SqliteSettingsStore settings;

    /**
     * Створює записувач із тим самим з'єднанням та сховищем типізованих налаштувань, що й репозиторій.
     *
     * @param connection з'єднання SQLite спільної транзакції.
     * @param settings сховище типізованих налаштувань проєкту.
     * @throws IllegalArgumentException якщо з'єднання або сховище не задано.
     */
    public SqliteMapWriter(java.sql.Connection connection, SqliteSettingsStore settings) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.connection = connection;
        this.settings = settings;
    }

    /**
     * Записує актуальний стан карти й камери.
     *
     * @param model модель карти нейронів.
     * @param cameraState стан камери, який потрібно зберегти разом із картою.
     * @throws SQLException якщо запис будь-якої частини карти не вдався.
     */
    public void save(NeuronMapModel model, CameraState cameraState) throws SQLException {
        deleteAllData();
        insertNeurons(model);
        insertPresentations(model);
        insertConnections(model);
        insertGroups(model);
        settings.saveCameraState(cameraState);
    }

    /** Очищає таблиці карти перед повним записом актуального стану. */
    private void deleteAllData() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM group_members");
            statement.executeUpdate("DELETE FROM groups");
            statement.executeUpdate("DELETE FROM connections");
            statement.executeUpdate("DELETE FROM neuron_presentations");
            statement.executeUpdate("DELETE FROM neurons");
        }
    }

    /**
     * Записує семантичні параметри всіх нейронів, включно з тривалістю активності.
     *
     * @param model модель карти, звідки беруться нейрони.
     * @throws SQLException якщо пакетний запис нейронів не вдався.
     */
    private void insertNeurons(NeuronMapModel model) throws SQLException {
        String sql = """
                INSERT INTO neurons (
                    id,
                    type,
                    activation,
                    signal_strength,
                    activation_threshold,
                    active_ticks
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Neuron neuron : model.neurons()) {
                statement.setString(1, neuron.id());
                statement.setString(2, neuron.type().name());
                statement.setInt(3, neuron.activation());
                statement.setInt(4, neuron.signalStrength());
                statement.setInt(5, neuron.activationThreshold());
                statement.setInt(6, neuron.activeTicks());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Записує координати, обертання й напрямок візуальних подань нейронів.
     *
     * @param model модель карти, що містить подання нейронів.
     * @throws SQLException якщо пакетний запис подань не вдався.
     */
    private void insertPresentations(NeuronMapModel model) throws SQLException {
        String sql = """
                INSERT INTO neuron_presentations (
                    neuron_id,
                    x,
                    y,
                    rotation,
                    direction_reversed
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (NeuronPresentation presentation : model.presentations()) {
                statement.setString(1, presentation.neuron().id());
                statement.setDouble(2, presentation.x());
                statement.setDouble(3, presentation.y());
                statement.setDouble(4, presentation.rotationDegrees());
                statement.setInt(5, presentation.directionReversed() ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Записує напрямлені зв'язки між нейронами.
     *
     * @param model модель карти, що містить зв'язки.
     * @throws SQLException якщо пакетний запис зв'язків не вдався.
     */
    private void insertConnections(NeuronMapModel model) throws SQLException {
        String sql = """
                INSERT INTO connections (
                    id,
                    source_id,
                    target_id
                )
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Connection connectionModel : model.connections()) {
                statement.setString(1, connectionModel.id());
                statement.setString(2, connectionModel.sourceId());
                statement.setString(3, connectionModel.targetId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Записує групи та кожного їхнього учасника.
     *
     * @param model модель карти, що містить групи та учасників.
     * @throws SQLException якщо пакетний запис груп або учасників не вдався.
     */
    private void insertGroups(NeuronMapModel model) throws SQLException {
        String groupSql = "INSERT INTO groups (id) VALUES (?)";
        String memberSql = """
                INSERT INTO group_members (
                    group_id,
                    neuron_id
                )
                VALUES (?, ?)
                """;

        try (PreparedStatement groupStatement = connection.prepareStatement(groupSql);
             PreparedStatement memberStatement = connection.prepareStatement(memberSql)) {
            for (NeuronGroup group : model.groups()) {
                groupStatement.setString(1, group.id());
                groupStatement.addBatch();
                for (String neuronId : group.memberIds()) {
                    memberStatement.setString(1, group.id());
                    memberStatement.setString(2, neuronId);
                    memberStatement.addBatch();
                }
            }
            groupStatement.executeBatch();
            memberStatement.executeBatch();
        }
    }
}
