package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що SQLite зберігає налаштування лише у власника структури та відновлює спільні посилання. */
class SqliteStructureSharedSettingsPersistenceTest {

    /** Перевіряє відсутність дубльованих налаштувань у рядку внутрішнього нейрона та коректне відновлення зв'язку. */
    @Test
    void persistsSharedSettingsOnlyOnStructureOwnerAndRestoresReferences(@TempDir Path tempDir) throws SQLException {
        Path databasePath = tempDir.resolve("shared-structure-settings.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron structure = original.createNeuron(NeuronType.STRUCTURE, 10.0, 20.0);
        Neuron member = original.createNeuron(NeuronType.EXCITATORY, 30.0, 40.0);
        structure.setName("Core structure");
        structure.setParameters(new NeuronParameters(8, 3, 4, 2));
        member.setName("Obsolete individual name");
        member.setParameters(new NeuronParameters(2, 1, 2, 1));
        assertTrue(original.addStructureMember(structure.id(), member.id()));

        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.save(original, CameraState.defaultState());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            assertNeuronRowHasDefaultSettings(connection, structure.id());
            assertNeuronRowHasDefaultSettings(connection, member.id());
            assertStructureSettingsRow(
                    connection, structure.id(), new NeuronParameters(8, 3, 4, 2), "Core structure"
            );
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.loadInto(restored);
        }

        Neuron restoredStructure = restored.neuron(structure.id());
        Neuron restoredMember = restored.neuron(member.id());
        assertEquals("Core structure", restoredMember.name());
        assertSame(restoredStructure.parameters(), restoredMember.parameters());
        assertEquals(new NeuronParameters(8, 3, 4, 2), restoredMember.parameters());

        restoredStructure.setName("Updated shared name");
        restoredStructure.setActivationMode(NeuronActivationMode.SIGNAL_COUNT);
        restoredStructure.setActivationThreshold(60);
        assertEquals("Updated shared name", restoredMember.name());
        assertEquals(NeuronActivationMode.SIGNAL_COUNT, restoredMember.activationMode());
        assertEquals(60, restoredMember.activationThreshold());

        assertTrue(restored.removeStructureMember(structure.id(), member.id()));
        assertNull(restored.structureContaining(member.id()));
        assertEquals("Updated shared name", restoredMember.name());
        assertEquals(60, restoredMember.activationThreshold());
        assertNotSame(restoredStructure.parameters(), restoredMember.parameters());
        restoredMember.setSignalStrength(14);
        assertEquals(14, restoredMember.signalStrength());
        assertEquals(8, restoredStructure.signalStrength());
    }


    /** Перевіряє перенесення раніше збережених налаштувань зі структурного нейрона до таблиці structures. */
    @Test
    void migratesLegacyStructureSettingsIntoStructureTable(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("legacy-shared-structure-settings.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY, type TEXT NOT NULL, activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1, activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1, delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH', name TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE structures (id TEXT PRIMARY KEY, expanded INTEGER NOT NULL DEFAULT 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO neurons (id, type, signal_strength, activation_threshold, active_ticks,
                                         delay_ticks, activation_mode, name)
                    VALUES ('legacy-structure', 'STRUCTURE', 9, 4, 6, 2, 'SIGNAL_STRENGTH', 'Legacy name')
                    """);
            // Стовпець expanded існує у тестовій структурі з попереднього SQL.
            //noinspection SqlResolve
            statement.executeUpdate("INSERT INTO structures (id, expanded) VALUES ('legacy-structure', 1)");
            statement.execute("PRAGMA user_version = 10");
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.loadInto(restored);
        }

        Neuron structure = restored.neuron("legacy-structure");
        assertEquals("Legacy name", structure.name());
        assertEquals(new NeuronParameters(9, 4, 6, 2), structure.parameters());
        assertTrue(restored.structure("legacy-structure").isExpanded());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            assertStructureSettingsRow(
                    connection, "legacy-structure", new NeuronParameters(9, 4, 6, 2), "Legacy name"
            );
        }
    }

    /** Перевіряє, що фізичний рядок нейрона містить лише технічні значення за замовчуванням. */
    private static void assertNeuronRowHasDefaultSettings(Connection connection, String neuronId)
            throws SQLException {
        // Стовпці забезпечуються SqliteSchema.migrate(), а їхні значення тут навмисно типові.
        //noinspection SqlResolve
        String sql = """
                SELECT signal_strength, activation_threshold, active_ticks, delay_ticks, activation_mode, name
                FROM neurons WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, neuronId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                NeuronParameters defaults = NeuronParameters.DEFAULT;
                assertEquals(defaults.signalStrength(), resultSet.getInt("signal_strength"));
                assertEquals(defaults.activationThreshold(), resultSet.getInt("activation_threshold"));
                assertEquals(defaults.activeTicks(), resultSet.getInt("active_ticks"));
                assertEquals(defaults.delayTicks(), resultSet.getInt("delay_ticks"));
                assertEquals(defaults.activationMode().name(), resultSet.getString("activation_mode"));
                assertNull(resultSet.getString("name"));
                assertFalse(resultSet.next());
            }
        }
    }

    /** Перевіряє спільні налаштування, що фізично зберігаються в рядку власника таблиці structures. */
    private static void assertStructureSettingsRow(
            Connection connection,
            String structureId,
            NeuronParameters expectedParameters,
            String expectedName
    ) throws SQLException {
        // Стовпці додаються міграцією; серед них можуть бути відсутні у старій БД.
        //noinspection SqlResolve
        String sql = """
                SELECT signal_strength, activation_threshold, active_ticks, delay_ticks, activation_mode, name
                FROM structures WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, structureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(expectedParameters.signalStrength(), resultSet.getInt("signal_strength"));
                assertEquals(expectedParameters.activationThreshold(), resultSet.getInt("activation_threshold"));
                assertEquals(expectedParameters.activeTicks(), resultSet.getInt("active_ticks"));
                assertEquals(expectedParameters.delayTicks(), resultSet.getInt("delay_ticks"));
                assertEquals(expectedParameters.activationMode().name(), resultSet.getString("activation_mode"));
                assertEquals(expectedName, resultSet.getString("name"));
                assertFalse(resultSet.next());
            }
        }
    }

}
