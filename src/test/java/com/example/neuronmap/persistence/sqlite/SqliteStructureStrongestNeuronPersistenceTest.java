package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.StructureNeuronCreationMode;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє міграцію, збереження режимів структури та їх успадкування вкладеними учасниками. */
final class SqliteStructureStrongestNeuronPersistenceTest {

    /** Перевіряє міграцію схеми версії 13 до актуальної версії без втрати наявних полів. */
    @Test
    void migratesVersionThirteenSchemaToCurrentStructureSettings(@TempDir Path tempDir) throws SQLException {
        Path database = tempDir.resolve("version-thirteen.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE structures (
                        id TEXT PRIMARY KEY,
                        expanded INTEGER NOT NULL DEFAULT 0,
                        name TEXT,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1,
                        delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH',
                        member_type TEXT CHECK (member_type IS NULL OR member_type IN ('EXCITATORY', 'INHIBITORY')),
                        direction_reversed INTEGER CHECK (direction_reversed IS NULL OR direction_reversed IN (0, 1)),
                        FOREIGN KEY (id) REFERENCES neurons(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("PRAGMA user_version = 13");
        }

        try (MapRepository ignored = new SqliteMapRepository(database);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            boolean foundColumn = false;
            boolean foundCreationModeColumn = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(structures)")) {
                while (columns.next()) {
                    if ("strongest_neuron_only".equals(columns.getString("name"))) {
                        foundColumn = true;
                    }
                    if ("neuron_creation_mode".equals(columns.getString("name"))) {
                        foundCreationModeColumn = true;
                    }
                }
            }
            assertTrue(foundColumn, "Міграція має додати прапорець режиму найсильнішого нейрона");
            assertTrue(foundCreationModeColumn, "Міграція має додати умову створення спільного нейрона");
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(16, version.getInt(1));
            }
        }
    }

    /** Перевіряє, що база версії 14 отримує нове поле режиму зі збереженням попередньої поведінки. */
    @Test
    void migratesVersionFourteenSchemaWithNeuronCountAsDefault(@TempDir Path tempDir) throws SQLException {
        Path database = tempDir.resolve("version-fourteen.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE structures (
                        id TEXT PRIMARY KEY,
                        expanded INTEGER NOT NULL DEFAULT 0,
                        name TEXT,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1,
                        delay_ticks INTEGER NOT NULL DEFAULT 1,
                        activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH',
                        member_type TEXT CHECK (member_type IS NULL OR member_type IN ('EXCITATORY', 'INHIBITORY')),
                        direction_reversed INTEGER CHECK (direction_reversed IS NULL OR direction_reversed IN (0, 1)),
                        strongest_neuron_only INTEGER CHECK (
                            strongest_neuron_only IS NULL OR strongest_neuron_only IN (0, 1)),
                        FOREIGN KEY (id) REFERENCES neurons(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("PRAGMA user_version = 14");
            SqliteSchema.migrate(connection);

            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(16, version.getInt(1));
            }
            boolean foundCreationMode = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(structures)")) {
                while (columns.next()) {
                    foundCreationMode |= "neuron_creation_mode".equals(columns.getString("name"));
                }
            }
            assertTrue(foundCreationMode);
        }
    }

    /** Перевіряє цикл SQLite та відсутність дублювання параметра в рядку вкладеної структури. */
    @Test
    void persistsFlagOnRootAndRestoresItForNestedMembers(@TempDir Path tempDir) throws SQLException {
        Path database = tempDir.resolve("strongest-neuron.db");
        NeuronMapModel source = new NeuronMapModel();
        Neuron root = source.createNeuron(NeuronType.STRUCTURE, 10.0, 10.0);
        Neuron nested = source.createNeuron(NeuronType.STRUCTURE, 20.0, 20.0);
        Neuron member = source.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(source.addStructureMember(root.id(), nested.id()));
        assertTrue(source.addStructureMember(nested.id(), member.id()));
        source.structure(root.id()).settings().setStrongestNeuronOnly(true);
        source.structure(root.id()).settings().setNeuronCreationMode(StructureNeuronCreationMode.STRUCTURE_COUNT);

        try (MapRepository repository = new SqliteMapRepository(database)) {
            repository.save(source, CameraState.defaultState());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             // Читаємо рядок цілком: поле режиму створення є частиною поточної схеми 16.
             ResultSet rows = statement.executeQuery("SELECT * FROM structures")) {
            boolean sawRoot = false;
            boolean sawNested = false;
            while (rows.next()) {
                if (root.id().equals(rows.getString("id"))) {
                    assertEquals(1, rows.getInt("strongest_neuron_only"));
                    assertEquals("STRUCTURE_COUNT", rows.getString("neuron_creation_mode"));
                    sawRoot = true;
                } else if (nested.id().equals(rows.getString("id"))) {
                    rows.getInt("strongest_neuron_only");
                    assertTrue(rows.wasNull(), "Вкладена структура має успадковувати прапорець від кореня");
                    assertNull(rows.getString("neuron_creation_mode"),
                            "Вкладена структура має успадковувати режим від кореня");
                    sawNested = true;
                }
            }
            assertTrue(sawRoot);
            assertTrue(sawNested);
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(database)) {
            repository.loadInto(restored);
        }
        assertTrue(restored.structure(root.id()).settings().strongestNeuronOnly());
        assertTrue(restored.structure(nested.id()).settings().strongestNeuronOnly());
        assertTrue(restored.neuron(member.id()).strongestNeuronOnly());
        assertEquals(StructureNeuronCreationMode.STRUCTURE_COUNT,
                restored.structure(root.id()).settings().neuronCreationMode());
        assertEquals(StructureNeuronCreationMode.STRUCTURE_COUNT,
                restored.structure(nested.id()).settings().neuronCreationMode());
        assertEquals(StructureNeuronCreationMode.STRUCTURE_COUNT, restored.neuron(member.id()).neuronCreationMode());
    }
}
