package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Створює та мігрує схему SQLite, додаючи потрібні таблиці й стовпці для актуальної версії даних. */
public final class SqliteSchema {

    private static final int STRUCTURE_SETTINGS_VERSION = 11;
    private static final int STRUCTURE_MEMBER_TYPE_VERSION = 12;
    private static final int CURRENT_VERSION = 14;

    /** Не дозволяє створювати екземпляри утиліти керування схемою. */
    private SqliteSchema() { }

    /**
     * Перевіряє версію схеми й застосовує необхідні зміни, не переписуючи актуальну базу без потреби.
     * Бази, створені новішою версією застосунку, відхиляються до будь-якої модифікації схеми.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws PersistenceException якщо версія схеми несумісна або підготовка бази не вдалася.
     */
    public static void migrate(Connection connection) {
        try {
            int versionAtStart = readVersion(connection);
            if (versionAtStart > CURRENT_VERSION) {
                throw new SQLException("Версія схеми SQLite " + versionAtStart
                        + " новіша за підтримувану версію " + CURRENT_VERSION + ".");
            }
            createBaseSchema(connection);
            migrateLegacyNeuronLayout(connection);
            ensureNeuronSettingsColumns(connection);
            ensureNameColumn(connection, "neurons");
            ensureActivationModeColumn(connection, "neurons");
            ensureDirectionColumn(connection);
            ensureStructureExpandedColumn(connection);
            ensureStructureSettingsColumns(connection);
            ensureStructureMemberTypeColumn(connection);
            ensureStructureDirectionColumn(connection);
            ensureStructureStrongestNeuronOnlyColumn(connection);
            if (versionAtStart < STRUCTURE_SETTINGS_VERSION) {
                migrateStructureSettingsFromNeuronRows(connection);
            }
            if (versionAtStart < STRUCTURE_MEMBER_TYPE_VERSION) {
                migrateStructureMemberTypeFromNeuronRows(connection);
            }
            if (versionAtStart < 13) {
                migrateStructureDirectionFromPresentations(connection);
            }
            ensureVersion(connection, versionAtStart);
        } catch (SQLException exception) {
            throw new PersistenceException("Не вдалося підготувати схему SQLite.", exception);
        }
    }

    /** Створює таблиці базової схеми лише тоді, коли вони ще не існують. */
    private static void createBaseSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS neurons (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        activation INTEGER NOT NULL DEFAULT 0,
                        signal_strength INTEGER NOT NULL DEFAULT 1,
                        activation_threshold INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS neuron_presentations (
                        neuron_id TEXT PRIMARY KEY,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        rotation REAL NOT NULL DEFAULT 0,
                        direction_reversed INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (neuron_id) REFERENCES neurons(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS connections (
                        id TEXT PRIMARY KEY,
                        source_id TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        FOREIGN KEY (source_id) REFERENCES neurons(id) ON DELETE CASCADE,
                        FOREIGN KEY (target_id) REFERENCES neurons(id) ON DELETE CASCADE,
                        UNIQUE (source_id, target_id),
                        CHECK (source_id <> target_id)
                    )
                    """);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS groups (id TEXT PRIMARY KEY)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        group_id TEXT NOT NULL,
                        neuron_id TEXT NOT NULL,
                        PRIMARY KEY (group_id, neuron_id),
                        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                        FOREIGN KEY (neuron_id) REFERENCES neurons(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS structures (
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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS structure_members (
                        structure_id TEXT NOT NULL,
                        neuron_id TEXT NOT NULL UNIQUE,
                        position_index INTEGER NOT NULL,
                        PRIMARY KEY (structure_id, neuron_id),
                        UNIQUE (structure_id, position_index),
                        FOREIGN KEY (structure_id) REFERENCES structures(id) ON DELETE CASCADE,
                        FOREIGN KEY (neuron_id) REFERENCES neurons(id) ON DELETE CASCADE,
                        CHECK (structure_id <> neuron_id),
                        CHECK (position_index >= 0)
                    )
                    """);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    /** Переносить координати нейронів зі старої таблиці до таблиці подань і видаляє застарілі стовпці. */
    private static void migrateLegacyNeuronLayout(Connection connection) throws SQLException {
        boolean hasX = columnExists(connection, "neurons", "x");
        boolean hasY = columnExists(connection, "neurons", "y");
        if (!hasX && !hasY) return;

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            if (hasX && hasY) {
                // Цей запит виконується лише для виявленої застарілої структури таблиці neurons.
                //noinspection SqlResolve
                statement.executeUpdate("""
                        INSERT OR IGNORE INTO neuron_presentations (neuron_id, x, y, rotation, direction_reversed)
                        SELECT id, x, y, 0, 0 FROM neurons
                        """);
            }
            if (hasX) {
                // Стовпець підтверджено як застарілий перевіркою структури таблиці.
                //noinspection SqlResolve
                statement.executeUpdate("ALTER TABLE neurons DROP COLUMN x");
            }
            if (hasY) {
                // Стовпець підтверджено як застарілий перевіркою структури таблиці.
                //noinspection SqlResolve
                statement.executeUpdate("ALTER TABLE neurons DROP COLUMN y");
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Додає відсутні числові параметри нейрона зі значенням за замовчуванням 1. */
    private static void ensureNeuronSettingsColumns(Connection connection) throws SQLException {
        ensureIntegerColumn(connection, "neurons", "signal_strength");
        ensureIntegerColumn(connection, "neurons", "activation_threshold");
        ensureIntegerColumn(connection, "neurons", "active_ticks");
        ensureIntegerColumn(connection, "neurons", "delay_ticks");
    }

    /** Додає параметри спільних налаштувань структури до старої таблиці structures. */
    private static void ensureStructureSettingsColumns(Connection connection) throws SQLException {
        ensureNameColumn(connection, "structures");
        ensureIntegerColumn(connection, "structures", "signal_strength");
        ensureIntegerColumn(connection, "structures", "activation_threshold");
        ensureIntegerColumn(connection, "structures", "active_ticks");
        ensureIntegerColumn(connection, "structures", "delay_ticks");
        ensureActivationModeColumn(connection, "structures");
    }

    /** Додає nullable-стовпець спільного типу учасників до старих і нових таблиць структур. */
    private static void ensureStructureMemberTypeColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "structures", "member_type")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE structures ADD COLUMN member_type TEXT
                    CHECK (member_type IS NULL OR member_type IN ('EXCITATORY', 'INHIBITORY'))
                    """);
        }
    }

    /** Додає nullable-стовпець спільного напрямку, який зберігається лише для кореневих структур. */
    private static void ensureStructureDirectionColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "structures", "direction_reversed")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE structures ADD COLUMN direction_reversed INTEGER
                    CHECK (direction_reversed IS NULL OR direction_reversed IN (0, 1))
                    """);
        }
    }

    /** Додає nullable-прапорець конкуренції; NULL використовується для вкладених структур зі спільними налаштуваннями. */
    private static void ensureStructureStrongestNeuronOnlyColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "structures", "strongest_neuron_only")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE structures ADD COLUMN strongest_neuron_only INTEGER
                    CHECK (strongest_neuron_only IS NULL OR strongest_neuron_only IN (0, 1))
                    """);
        }
    }

    /** Додає відсутній стовпець цілого параметра в указаній таблиці зі значенням за замовчуванням 1. */
    private static void ensureIntegerColumn(Connection connection, String table, String column) throws SQLException {
        if (columnExists(connection, table, column)) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " INTEGER NOT NULL DEFAULT 1");
        }
    }

    /** Додає nullable-стовпець назви, не змінюючи наявні рядки таблиці. */
    private static void ensureNameColumn(Connection connection, String table) throws SQLException {
        if (columnExists(connection, table, "name")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN name TEXT");
        }
    }

    /** Додає режим активації до таблиці; наявні рядки залишаються в режимі сили сигналів. */
    private static void ensureActivationModeColumn(Connection connection, String table) throws SQLException {
        if (columnExists(connection, table, "activation_mode")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table
                    + " ADD COLUMN activation_mode TEXT NOT NULL DEFAULT 'SIGNAL_STRENGTH'");
        }
    }

    /** Додає стовпець напрямку нейрона, якщо його ще немає в таблиці подань. */
    private static void ensureDirectionColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "neuron_presentations", "direction_reversed")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE neuron_presentations ADD COLUMN direction_reversed INTEGER NOT NULL DEFAULT 0");
        }
    }

    /** Додає стан відкритості до наявної таблиці структур; старі структури залишаються закритими. */
    private static void ensureStructureExpandedColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "structures", "expanded")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE structures ADD COLUMN expanded INTEGER NOT NULL DEFAULT 0");
        }
    }

    /**
     * Переносить налаштування структур із рядків нейронів до таблиці structures під час міграції до версії 11.
     * Для старих баз, у яких ще немає рядків структур, створює їх для структурних нейронів.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо перенесення даних не вдалося.
     */
    private static void migrateStructureSettingsFromNeuronRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT OR IGNORE INTO structures (id) SELECT id FROM neurons WHERE type = 'STRUCTURE'");
            // Поля structures додаються вище міграційними ensure-методами.
            //noinspection SqlResolve
            statement.executeUpdate("""
                    UPDATE structures
                    SET name = (SELECT name FROM neurons WHERE neurons.id = structures.id),
                        signal_strength = (SELECT signal_strength FROM neurons WHERE neurons.id = structures.id),
                        activation_threshold = (SELECT activation_threshold FROM neurons WHERE neurons.id = structures.id),
                        active_ticks = (SELECT active_ticks FROM neurons WHERE neurons.id = structures.id),
                        delay_ticks = (SELECT delay_ticks FROM neurons WHERE neurons.id = structures.id),
                        activation_mode = (SELECT activation_mode FROM neurons WHERE neurons.id = structures.id)
                    WHERE EXISTS (SELECT 1 FROM neurons WHERE neurons.id = structures.id)
                    """);
        }
    }

    /**
     * Відновлює спільний тип наявних структур за першим звичайним учасником у їхньому дереві.
     * Порожні структури залишають тип невизначеним і використовують типовий збуджувальний тип до першого учасника.
     *
     * @param connection відкрите з'єднання з базою SQLite.
     * @throws SQLException якщо міграція типів учасників не вдалася.
     */
    private static void migrateStructureMemberTypeFromNeuronRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // IntelliJ не може статично вивести схему після ALTER TABLE у міграціях.
            //noinspection SqlResolve
            statement.executeUpdate("""
                    WITH RECURSIVE member_tree(root_id, neuron_id, member_path) AS (
                        SELECT structure_id, neuron_id, printf('%010d', position_index) FROM structure_members
                        UNION ALL
                        SELECT member_tree.root_id, structure_members.neuron_id,
                               member_tree.member_path || '.' || printf('%010d', structure_members.position_index)
                        FROM member_tree JOIN structure_members ON structure_members.structure_id = member_tree.neuron_id
                    )
                    UPDATE structures
                    SET member_type = (
                        SELECT neurons.type FROM member_tree JOIN neurons ON neurons.id = member_tree.neuron_id
                        WHERE member_tree.root_id = structures.id AND neurons.type IN ('EXCITATORY', 'INHIBITORY')
                        ORDER BY member_tree.member_path LIMIT 1
                    )
                    WHERE member_type IS NULL
                      AND NOT EXISTS (SELECT 1 FROM structure_members WHERE structure_members.neuron_id = structures.id)
                    """);
        }
    }

    /** Переносить напрямок кореневих структур із рядків подань до їхнього єдиного джерела правди. */
    private static void migrateStructureDirectionFromPresentations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Стовпці додані ensure-методами в межах цієї ж міграції.
            //noinspection SqlResolve
            statement.executeUpdate("""
                    UPDATE structures SET direction_reversed = COALESCE((
                        SELECT neuron_presentations.direction_reversed FROM neuron_presentations
                        WHERE neuron_presentations.neuron_id = structures.id
                    ), 0)
                    WHERE NOT EXISTS (SELECT 1 FROM structure_members WHERE structure_members.neuron_id = structures.id)
                    """);
            //noinspection SqlResolve
            statement.executeUpdate("""
                    UPDATE structures SET member_type = NULL, direction_reversed = NULL
                    WHERE EXISTS (SELECT 1 FROM structure_members WHERE structure_members.neuron_id = structures.id)
                    """);
        }
    }

    /** Перевіряє наявність стовпця в таблиці без зміни даних. */
    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String sql = "PRAGMA table_info(" + table + ")";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) return true;
            }
        }
        return false;
    }

    /** Читає записану версію схеми, не змінюючи заголовок бази даних. */
    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) throw new SQLException("SQLite не повернула значення user_version.");
            return resultSet.getInt(1);
        }
    }

    /** Записує версію схеми лише після першого створення або успішного оновлення старішої схеми. */
    private static void ensureVersion(Connection connection, int versionAtStart) throws SQLException {
        if (versionAtStart == CURRENT_VERSION) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + CURRENT_VERSION);
        }
    }
}
