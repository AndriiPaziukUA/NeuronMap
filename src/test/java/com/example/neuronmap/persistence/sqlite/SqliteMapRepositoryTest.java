package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.persistence.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Перевіряє збереження та відновлення семантики нейронів, структур і подань у SQLite. */
class SqliteMapRepositoryTest {

    /** Перевіряє круговий цикл збереження й завантаження параметрів нейронів, зв'язків, груп і камери. */
    @Test
    void roundTripPersistsSemanticPresentationConnectionsGroupsAndCamera(@TempDir Path tempDir) {
        Path db = tempDir.resolve("map.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron a = original.createNeuron(NeuronType.EXCITATORY, 10.5, -20.25, 45.0);
        Neuron b = original.createNeuron(NeuronType.INHIBITORY, 300.0, 90.0, 275.0);

        a.setActivation(7);
        a.setParameters(new NeuronParameters(6, 0, 5, 6, NeuronActivationMode.SIGNAL_COUNT));
        a.setName("Input ⚡ #1");
        b.setActivation(-2);
        b.setSignalStrength(3);
        b.setActivationThreshold(8);
        b.setActiveTicks(3);
        b.setDelayTicks(2);
        b.setName(null);

        original.presentation(a.id()).setDirectionReversed(true);
        original.presentation(b.id()).setDirectionReversed(false);
        assertTrue(original.createConnection(a.id(), b.id()));
        original.createGroup(new LinkedHashSet<>(Set.of(a.id(), b.id())));

        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.save(original, new CameraState(1.75, -320, 85));
        }
        assertTrue(Files.exists(db));

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(restored);
            assertEquals(new CameraState(1.75, -320, 85), repository.loadCameraState());
        }

        assertEquals(2, restored.neurons().size());
        assertEquals(1, restored.connections().size());
        assertEquals(1, restored.groups().size());
        assertEquals(10.5, restored.presentation(a.id()).x());
        assertEquals(-20.25, restored.presentation(a.id()).y());
        assertEquals(45.0, restored.presentation(a.id()).rotationDegrees());
        assertTrue(restored.presentation(a.id()).directionReversed());
        assertEquals(6, restored.neuron(a.id()).signalStrength());
        assertEquals(0, restored.neuron(a.id()).activationThreshold());
        assertEquals(5, restored.neuron(a.id()).activeTicks());
        assertEquals(6, restored.neuron(a.id()).delayTicks());
        assertEquals(NeuronActivationMode.SIGNAL_COUNT, restored.neuron(a.id()).activationMode());
        assertEquals("Input ⚡ #1", restored.neuron(a.id()).name());
        assertNull(restored.neuron(b.id()).name());
        assertEquals(-2, restored.neuron(b.id()).activation());
        assertEquals(3, restored.neuron(b.id()).signalStrength());
        assertEquals(8, restored.neuron(b.id()).activationThreshold());
        assertEquals(3, restored.neuron(b.id()).activeTicks());
        assertEquals(2, restored.neuron(b.id()).delayTicks());
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH, restored.neuron(b.id()).activationMode());
        assertEquals(SignalType.STATIC, restored.neuron(a.id()).signalType());
        assertFalse(restored.presentation(b.id()).directionReversed());
    }

    /** Перевіряє, що персональні сили учасників адаптивної структури не зливаються під час збереження. */
    @Test
    void roundTripPersistsPersonalSignalStrengthsAndSharedSignalTypeOfStructureMembers(@TempDir Path tempDir) {
        Path db = tempDir.resolve("adaptive-structure.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron structure = original.createNeuron(NeuronType.STRUCTURE, 42.0, 84.0);
        Neuron firstMember = original.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        Neuron secondMember = original.createNeuron(NeuronType.EXCITATORY, 0.0, 0.0);
        assertTrue(original.addStructureMember(structure.id(), firstMember.id()));
        assertTrue(original.addStructureMember(structure.id(), secondMember.id()));
        structure.setParameters(new NeuronParameters(
                10, 1, 1, 1, NeuronActivationMode.SIGNAL_STRENGTH, SignalType.RECEIVED));
        firstMember.setSignalStrength(3);
        secondMember.setSignalStrength(7);

        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.save(original, CameraState.defaultState());
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(restored);
        }

        assertEquals(SignalType.RECEIVED, restored.neuron(structure.id()).signalType());
        assertEquals(SignalType.RECEIVED, restored.neuron(firstMember.id()).signalType());
        assertEquals(SignalType.RECEIVED, restored.neuron(secondMember.id()).signalType());
        assertEquals(3, restored.neuron(firstMember.id()).signalStrength());
        assertEquals(7, restored.neuron(secondMember.id()).signalStrength());
    }

    /** Перевіряє, що новий структурний тип проходить крізь стандартний цикл збереження та завантаження. */
    @Test
    void roundTripPersistsStructureNeuronType(@TempDir Path tempDir) {
        Path db = tempDir.resolve("structure.db");
        NeuronMapModel original = new NeuronMapModel();
        Neuron structure = original.createNeuron(NeuronType.STRUCTURE, 42.0, 84.0);

        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.save(original, new CameraState(1.0, 0.0, 0.0));
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(restored);
        }

        assertEquals(NeuronType.STRUCTURE, restored.neuron(structure.id()).type());
        assertEquals(42.0, restored.presentation(structure.id()).x());
        assertEquals(84.0, restored.presentation(structure.id()).y());
    }

    /** Перевіряє типові режими сигналу для нейронів із бази старої версії. */
    @Test
    void oldSchemaDefaultsExistingNeuronsToStaticSignalMode(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1,
                        active_ticks INTEGER NOT NULL DEFAULT 1,
                        delay_ticks INTEGER NOT NULL DEFAULT 1,
                        name TEXT
                    )
                    """);
            statement.executeUpdate("INSERT INTO neurons (id, type, activation_threshold) VALUES ('legacy', 'EXCITATORY', 4)");
            statement.execute("PRAGMA user_version = 7");
        }

        NeuronMapModel restored = new NeuronMapModel();
        try (MapRepository repository = new SqliteMapRepository(db)) {
            repository.loadInto(restored);
        }

        assertEquals(4, restored.neuron("legacy").activationThreshold());
        assertEquals(NeuronActivationMode.SIGNAL_STRENGTH, restored.neuron("legacy").activationMode());
        assertEquals(SignalType.STATIC, restored.neuron("legacy").signalType());
    }

    /** Перевіряє, що фабрика налаштовує надійний режим журналювання та конкурентний доступ SQLite. */
    @Test
    void configuresDurableSqlitePragmas(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("pragmas.db");
        try (Connection connection = SqliteConnectionFactory.open(db);
             Statement statement = connection.createStatement()) {
            assertEquals("wal", pragmaText(statement));
            assertEquals(2, pragmaInt(statement, "PRAGMA synchronous"));
            assertEquals(1, pragmaInt(statement, "PRAGMA foreign_keys"));
            assertEquals(5000, pragmaInt(statement, "PRAGMA busy_timeout"));
        }
    }

    /** Перевіряє відокремлення семантичних параметрів нейрона від координат його візуального подання. */
    @Test
    void schemaKeepsNeuronAndPresentationDataSeparate(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("schema.db");
        try (MapRepository ignored = new SqliteMapRepository(db);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            assertEquals(
                    Set.of("id", "type", "activation", "signal_strength", "activation_threshold",
                            "active_ticks", "delay_ticks", "name", "activation_mode", "signal_type"),
                    columnNames(connection, "neurons")
            );
            assertEquals(
                    Set.of("id", "expanded", "name", "signal_strength", "activation_threshold", "active_ticks",
                            "delay_ticks", "activation_mode", "member_type", "direction_reversed",
                            "strongest_neuron_only", "neuron_creation_mode", "signal_type"),
                    columnNames(connection, "structures")
            );
            assertEquals(
                    Set.of("neuron_id", "x", "y", "rotation", "direction_reversed"),
                    columnNames(connection, "neuron_presentations")
            );
        }
    }

    /** Читає текстове значення PRAGMA для перевірки налаштувань підключення SQLite. */
    private static String pragmaText(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA journal_mode")) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    /** Читає ціле значення PRAGMA для перевірки параметрів підключення SQLite. */
    private static int pragmaInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    /** Повертає назви стовпців указаного столу без залежності від порядку. */
    private static Set<String> columnNames(Connection connection, String table) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        }
        return names;
    }
}
