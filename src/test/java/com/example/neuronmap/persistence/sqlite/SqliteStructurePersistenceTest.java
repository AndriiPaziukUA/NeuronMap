package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.StructureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє збереження, міграцію та відновлення структури й стану її шухлядки у SQLite. */
class SqliteStructurePersistenceTest {

    /** Перевіряє відновлення відкритості, порожніх контейнерів, вкладеності й порядку учасників. */
    @Test
    void roundTripPreservesStructureEntitiesMembershipAndDrawerState(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("structures.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron rootNeuron = original.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron nestedNeuron = original.createNeuron(NeuronType.STRUCTURE, 100, 120);
        Neuron emptyStructureNeuron = original.createNeuron(NeuronType.STRUCTURE, 160, 180);
        Neuron firstMember = original.createNeuron(NeuronType.EXCITATORY, 30, 40);
        Neuron secondMember = original.createNeuron(NeuronType.INHIBITORY, 50, 60);
        StructureService structures = new StructureService(original);

        assertTrue(structures.addMember(rootNeuron.id(), firstMember.id()));
        assertTrue(structures.addMember(rootNeuron.id(), nestedNeuron.id()));
        assertTrue(structures.addMember(nestedNeuron.id(), secondMember.id()));
        assertTrue(structures.setExpanded(rootNeuron.id(), true));
        assertTrue(structures.setExpanded(nestedNeuron.id(), true));
        assertTrue(structures.setExpanded(emptyStructureNeuron.id(), true));

        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.save(original, CameraState.defaultState());
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.loadInto(restored);
        }

        NeuronStructure restoredRoot = restored.structure(rootNeuron.id());
        NeuronStructure restoredNested = restored.structure(nestedNeuron.id());
        NeuronStructure restoredEmpty = restored.structure(emptyStructureNeuron.id());
        assertEquals(List.of(firstMember.id(), nestedNeuron.id()), List.copyOf(restoredRoot.memberIds()));
        assertEquals(Set.of(secondMember.id()), restoredNested.memberIds());
        assertTrue(restoredEmpty.memberIds().isEmpty());
        assertTrue(restoredRoot.isExpanded());
        assertTrue(restoredNested.isExpanded());
        assertTrue(restoredEmpty.isExpanded());
        assertEquals(3, restored.structures().size());
        assertEquals(5, restored.neurons().size());
    }

    /**
     * Перевіряє, що завантаження відхиляє старий збережений зв’язок між двома учасниками однієї структури.
     */
    @Test
    void loadingRejectsLegacyConnectionsInsideOneStructure(@TempDir Path tempDir) throws SQLException {
        Path databasePath = tempDir.resolve("invalid-internal-connection.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron structure = original.createNeuron(NeuronType.STRUCTURE, 10, 20);
        Neuron firstMember = original.createNeuron(NeuronType.EXCITATORY, 30, 40);
        Neuron secondMember = original.createNeuron(NeuronType.INHIBITORY, 50, 60);
        assertTrue(original.addStructureMember(structure.id(), firstMember.id()));
        assertTrue(original.addStructureMember(structure.id(), secondMember.id()));

        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.save(original, CameraState.defaultState());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO connections (id, source_id, target_id) VALUES (?, ?, ?)")) {
            statement.setString(1, "legacy-internal-connection");
            statement.setString(2, firstMember.id());
            statement.setString(3, secondMember.id());
            assertEquals(1, statement.executeUpdate());
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.loadInto(restored);
        }

        assertTrue(restored.connections().isEmpty());
        assertEquals(Set.of(firstMember.id(), secondMember.id()), restored.structure(structure.id()).memberIds());
    }

    /** Перевіряє, що структури та їхнє членство зберігаються окремо й мають індекс порядку учасників. */
    @Test
    void schemaStoresStructureMetadataSeparatelyFromNeuronRows(@TempDir Path tempDir) throws SQLException {
        Path databasePath = tempDir.resolve("structure-schema.db");
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 10, 10);
        assertTrue(new StructureService(model).addMember(structure.id(), member.id()));

        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.save(model, CameraState.defaultState());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "SELECT COUNT(*) FROM structures"));
            assertEquals(1, count(statement, "SELECT COUNT(*) FROM structure_members"));
            assertTrue(hasStructureColumn(statement, "structure_members", "position_index"));
            assertTrue(hasStructureColumn(statement, "structures", "expanded"));
        }
    }

    /** Перевіряє міграцію бази дев'ятої версії та закритий стан для раніше збережених структур. */
    @Test
    void migrationAddsClosedDrawerStateToExistingStructures(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("legacy-structures.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.executeUpdate("CREATE TABLE structures (id TEXT PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO neurons (id, type) VALUES ('legacy-structure', 'STRUCTURE')");
            statement.executeUpdate("INSERT INTO structures (id) VALUES ('legacy-structure')");
            statement.execute("PRAGMA user_version = 9");
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(databasePath)) {
            repository.loadInto(restored);
        }

        assertEquals(1, restored.structures().size());
        assertFalse(restored.structure("legacy-structure").isExpanded());
    }

    /** Повертає кількість рядків запиту, який містить один агрегований результат.
     * @param statement оператор для виконання SQL-запиту.
     * @param sql запит, що повертає одне значення кількості.
     * @return кількість рядків, визначена запитом.
     * @throws SQLException якщо виконання запиту завершується помилкою.
     */
    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    /** Перевіряє, чи таблиця структур має вказаний стовпець для порядку учасників або відкритості.
     * @param statement відкритий оператор для читання метаданих SQLite.
     * @param table назва таблиці, метадані якої перевіряються.
     * @param column назва стовпця, наявність якого перевіряється.
     * @return {@code true}, якщо стовпець існує.
     * @throws SQLException якщо SQLite не вдалося прочитати метадані таблиці.
     */
    private static boolean hasStructureColumn(Statement statement, String table, String column)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
