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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє окрему схему зберігання структур та круговий цикл членства у SQLite. */
class SqliteStructurePersistenceTest {

    /** Перевіряє відновлення порожніх структур, вкладеності та порядку учасників після перезапуску репозиторію. */
    @Test
    void roundTripPreservesStructureEntitiesAndOrderedMembership(@TempDir Path tempDir) {
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
        assertEquals(3, restored.structures().size());
        assertEquals(5, restored.neurons().size());
    }

    /** Перевіряє, що контейнери та їх членство мають окремі таблиці й підтримують індекс порядку учасника.
     *
     * @throws SQLException якщо перевірка метаданих SQLite завершується помилкою.
     */
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
            assertTrue(hasStructureMemberPositionIndex(statement));
        }
    }

    /** Повертає кількість рядків запиту, який містить один агрегований результат.
     *
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

    /** Перевіряє, що таблиця членства структур містить стовпець для відновлення порядку учасників.
     *
     * @param statement відкритий оператор для читання метаданих SQLite.
     * @return {@code true}, якщо стовпець {@code position_index} існує.
     * @throws SQLException якщо SQLite не вдалося прочитати метадані таблиці.
     */
    private static boolean hasStructureMemberPositionIndex(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(structure_members)")) {
            while (resultSet.next()) {
                if ("position_index".equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
