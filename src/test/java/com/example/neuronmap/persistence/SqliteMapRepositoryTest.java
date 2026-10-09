package com.example.neuronmap.persistence;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
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

/**
 * Перевіряє повне збереження та відновлення семантичних даних, подання нейронів, зв’язків, груп і камери в SQLite.
 */
class SqliteMapRepositoryTest {

    /**
     * Перевіряє круговий цикл збереження та завантаження моделі, груп, зв’язків і камери.
     */
    @Test
    void roundTripPersistsSemanticPresentationConnectionsGroupsAndCamera(
            @TempDir Path tempDir
    ) {
        Path db = tempDir.resolve("map.db");
        NeuronMapModel original = new NeuronMapModel();

        Neuron a = original.createNeuron(
                NeuronType.EXCITATORY,
                10.5,
                -20.25,
                45.0
        );
        Neuron b = original.createNeuron(
                NeuronType.INHIBITORY,
                300.0,
                90.0,
                275.0
        );

        a.setActivation(7);
        a.setSignalStrength(6);
        a.setActivationThreshold(4);
        b.setActivation(-2);
        b.setSignalStrength(3);
        b.setActivationThreshold(8);

        original.presentation(a.id())
                .setDirectionReversed(true);
        original.presentation(b.id())
                .setDirectionReversed(false);

        assertTrue(
                original.createConnection(a.id(), b.id())
        );
        original.createGroup(
                new LinkedHashSet<>(Set.of(a.id(), b.id()))
        );

        try (MapRepository repository =
                     new SqliteMapRepository(db)) {
            repository.save(
                    original,
                    new CameraState(1.75, -320, 85)
            );
        }

        assertTrue(Files.exists(db));

        NeuronMapModel restored = new NeuronMapModel();

        try (MapRepository repository =
                     new SqliteMapRepository(db)) {

            repository.loadInto(restored);

            assertEquals(
                    new CameraState(1.75, -320, 85),
                    repository.loadCameraState()
            );
        }

        assertEquals(2, restored.neurons().size());
        assertEquals(1, restored.connections().size());
        assertEquals(1, restored.groups().size());

        assertEquals(
                10.5,
                restored.presentation(a.id()).x()
        );
        assertEquals(
                -20.25,
                restored.presentation(a.id()).y()
        );
        assertEquals(
                45.0,
                restored.presentation(a.id()).rotationDegrees()
        );
        assertTrue(
                restored.presentation(a.id()).directionReversed()
        );

        assertEquals(
                6,
                restored.neuron(a.id()).signalStrength()
        );
        assertEquals(
                4,
                restored.neuron(a.id()).activationThreshold()
        );

        assertEquals(
                -2,
                restored.neuron(b.id()).activation()
        );
        assertEquals(
                3,
                restored.neuron(b.id()).signalStrength()
        );
        assertEquals(
                8,
                restored.neuron(b.id()).activationThreshold()
        );
        assertFalse(
                restored.presentation(b.id()).directionReversed()
        );
    }

    @Test
    void configuresDurableSqlitePragmas(
            @TempDir Path tempDir
    ) throws Exception {
        Path db = tempDir.resolve("pragmas.db");

        try (Connection connection =
                     SqliteConnectionFactory.open(db);
             Statement statement =
                     connection.createStatement()) {

            assertEquals(
                    "wal",
                    pragmaText(
                            statement,
                            "PRAGMA journal_mode"
                    )
            );

            assertEquals(
                    2,
                    pragmaInt(
                            statement,
                            "PRAGMA synchronous"
                    )
            );

            assertEquals(
                    1,
                    pragmaInt(
                            statement,
                            "PRAGMA foreign_keys"
                    )
            );

            assertEquals(
                    5000,
                    pragmaInt(
                            statement,
                            "PRAGMA busy_timeout"
                    )
            );
        }
    }

    @Test
    void schemaKeepsNeuronAndPresentationDataSeparate(
            @TempDir Path tempDir
    ) throws Exception {
        Path db = tempDir.resolve("schema.db");

        try (MapRepository ignored =
                     new SqliteMapRepository(db);
             Connection connection =
                     DriverManager.getConnection(
                             "jdbc:sqlite:" + db.toAbsolutePath()
                     )) {

            assertEquals(
                    Set.of(
                            "id",
                            "type",
                            "activation",
                            "signal_strength",
                            "activation_threshold"
                    ),
                    columnNames(
                            connection,
                            "neurons"
                    )
            );

            assertEquals(
                    Set.of(
                            "neuron_id",
                            "x",
                            "y",
                            "rotation",
                            "direction_reversed"
                    ),
                    columnNames(
                            connection,
                            "neuron_presentations"
                    )
            );
        }
    }

    private static String pragmaText(
            Statement statement,
            String sql
    ) throws Exception {
        try (ResultSet resultSet =
                     statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static int pragmaInt(
            Statement statement,
            String sql
    ) throws Exception {
        try (ResultSet resultSet =
                     statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static Set<String> columnNames(
            Connection connection,
            String table
    ) throws Exception {
        Set<String> names = new LinkedHashSet<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "PRAGMA table_info(" + table + ")"
             )) {

            while (resultSet.next()) {
                names.add(
                        resultSet.getString("name")
                );
            }
        }

        return names;
    }
}
