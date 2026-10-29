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
     * Записує актуальний стан карти, включно з групами, структурами та станом камери.
     *
     * @param model модель карти для збереження.
     * @param cameraState стан камери для збереження разом із картою.
     * @throws SQLException якщо запис будь-якої частини карти завершився помилкою.
     */
    public void save(NeuronMapModel model, CameraState cameraState) throws SQLException {
        deleteAllData();
        insertNeurons(model);
        insertPresentations(model);
        insertConnections(model);
        insertGroups(model);
        insertStructures(model);
        settings.saveCameraState(cameraState);
    }

    /**
     * Очищає залежні таблиці карти перед повним записом актуального стану в межах транзакції репозиторію.
     *
     * @throws SQLException якщо будь-яку з таблиць не вдалося очистити.
     */
    private void deleteAllData() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Збереження замінює всю карту в межах транзакції репозиторію.
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM structure_members");
            // Спочатку видаляються членства та контейнери, що залежать від нейронів.
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM structures");
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM group_members");
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM groups");
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM connections");
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM neuron_presentations");
            //noinspection SqlWithoutWhere
            statement.executeUpdate("DELETE FROM neurons");
        }
    }

    /**
     * Записує параметри кожного нейрона, зберігаючи спільні налаштування лише в рядку структури-власника.
     *
     * <p>Для звичайних учасників структур у таблиці зберігаються технічний тип {@code EXCITATORY},
     * типові параметри й порожня назва. Ефективний тип має єдине джерело правди в
     * {@code structures.member_type} та відновлюється під час прив'язування до спільних налаштувань.
     * Самі структурні контейнери продовжують записуватися з типом {@code STRUCTURE}.</p>
     *
     * @param model модель карти з нейронами та структурним членством.
     * @throws SQLException якщо пакетний запис нейронів не вдався.
     */
    private void insertNeurons(NeuronMapModel model) throws SQLException {
        // SqliteSchema.migrate() забезпечує наявність стовпців для всіх параметрів нейрона.
        //noinspection SqlResolve
        String sql = """
                INSERT INTO neurons (
                    id,
                    type,
                    activation,
                    signal_strength,
                    activation_threshold,
                    active_ticks,
                    delay_ticks,
                    activation_mode,
                    name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Neuron neuron : model.neurons()) {
                boolean sharesStructureSettings = model.structure(neuron.id()) != null
                        || model.structureContaining(neuron.id()) != null;
                NeuronParameters parameters = sharesStructureSettings
                        ? NeuronParameters.DEFAULT
                        : neuron.parameters();
                statement.setString(1, neuron.id());
                String storedType = model.structure(neuron.id()) != null
                        ? NeuronType.STRUCTURE.name()
                        : model.structureContaining(neuron.id()) != null
                                ? NeuronType.EXCITATORY.name()
                                : neuron.type().name();
                statement.setString(2, storedType);
                statement.setInt(3, neuron.activation());
                statement.setInt(4, parameters.signalStrength());
                statement.setInt(5, parameters.activationThreshold());
                statement.setInt(6, parameters.activeTicks());
                statement.setInt(7, parameters.delayTicks());
                statement.setString(8, parameters.activationMode().name());
                statement.setString(9, sharesStructureSettings ? null : neuron.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Записує координати, обертання й ефективний напрямок візуальних подань нейронів.
     * Для учасників структури {@code directionReversed()} повертає напрямок спільного власника.
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
                boolean hasStructureOwnedDirection = model.structure(presentation.neuron().id()) != null
                        || model.structureContaining(presentation.neuron().id()) != null;
                statement.setInt(5, hasStructureOwnedDirection || !presentation.directionReversed()
                        ? 0 : 1);
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
     * @param model модель карти, що містить групи та їхніх учасників.
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

    /**
     * Записує спільні налаштування кореневих структур, стан шухлядки, тип і напрямок учасників та членство.
     * Для вкладених структур у поля налаштувань записуються технічні значення й {@code NULL} для спільних полів.
     *
     * @param model модель карти, що містить структури.
     * @throws SQLException якщо запис структур або їхніх учасників не вдався.
     */
    private void insertStructures(NeuronMapModel model) throws SQLException {
        // SqliteSchema.migrate() забезпечує всі поля спільних налаштувань у таблиці structures.
        //noinspection SqlResolve
        String structureSql = """
                INSERT INTO structures (
                    id, expanded, name, signal_strength, activation_threshold,
                    active_ticks, delay_ticks, activation_mode, member_type, direction_reversed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String memberSql = """
                INSERT INTO structure_members (
                    structure_id,
                    neuron_id,
                    position_index
                )
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement structureStatement = connection.prepareStatement(structureSql);
             PreparedStatement memberStatement = connection.prepareStatement(memberSql)) {
            for (NeuronStructure structure : model.structures()) {
                boolean sharesParentSettings = model.structureContaining(structure.id()) != null;
                NeuronSettings structureSettings = structure.settings();
                NeuronParameters parameters = sharesParentSettings
                        ? NeuronParameters.DEFAULT
                        : structureSettings.parameters();
                structureStatement.setString(1, structure.id());
                structureStatement.setInt(2, structure.isExpanded() ? 1 : 0);
                structureStatement.setString(3, sharesParentSettings ? null : structureSettings.name());
                structureStatement.setInt(4, parameters.signalStrength());
                structureStatement.setInt(5, parameters.activationThreshold());
                structureStatement.setInt(6, parameters.activeTicks());
                structureStatement.setInt(7, parameters.delayTicks());
                structureStatement.setString(8, parameters.activationMode().name());
                structureStatement.setString(9, sharesParentSettings ? null : structureSettings.memberType().name());
                if (sharesParentSettings) {
                    structureStatement.setNull(10, java.sql.Types.INTEGER);
                } else {
                    structureStatement.setInt(10, structureSettings.directionReversed() ? 1 : 0);
                }
                structureStatement.addBatch();

                int position = 0;
                for (String neuronId : structure.memberIds()) {
                    memberStatement.setString(1, structure.id());
                    memberStatement.setString(2, neuronId);
                    memberStatement.setInt(3, position++);
                    memberStatement.addBatch();
                }
            }
            structureStatement.executeBatch();
            memberStatement.executeBatch();
        }
    }
}
