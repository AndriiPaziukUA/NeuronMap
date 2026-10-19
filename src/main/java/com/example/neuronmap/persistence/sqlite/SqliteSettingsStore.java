package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.persistence.PersistenceException;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Читає й записує типізовані налаштування проєкту в таблиці SQLite. */
public final class SqliteSettingsStore {

    private static final Logger LOGGER = Logger.getLogger(SqliteSettingsStore.class.getName());

    public static final String SIMULATION_TICK_MILLIS_KEY = "simulation_tick_millis";
    public static final String CAMERA_ZOOM_KEY = "zoom";
    public static final String CAMERA_PAN_X_KEY = "panX";
    public static final String CAMERA_PAN_Y_KEY = "panY";

    private final java.sql.Connection connection;

    /**
     * Створює сховище налаштувань, що працює через передане з'єднання та бере участь у його транзакціях.
     *
     * @param connection спільне з'єднання SQLite активного репозиторію.
     * @throws IllegalArgumentException якщо з'єднання не задано.
     */
    public SqliteSettingsStore(java.sql.Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
    }

    /**
     * Зчитує значення налаштування за ключем із таблиці налаштувань.
     *
     * @param key ключ налаштування.
     * @return текстове значення або {@code null}, якщо ключ не збережено.
     * @throws SQLException якщо SQLite не може виконати запит.
     */
    public String load(String key) throws SQLException {
        String sql = """
                SELECT value
                FROM settings
                WHERE key = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("value") : null;
            }
        }
    }

    /**
     * Записує значення налаштування за ключем у таблицю налаштувань.
     *
     * @param key ключ налаштування.
     * @param value значення, яке потрібно зберегти.
     * @throws SQLException якщо SQLite не може виконати запис.
     */
    public void write(String key, String value) throws SQLException {
        String sql = """
                INSERT INTO settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE
                SET value = excluded.value
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    /**
     * Завантажує стан камери з трьох налаштувань, централізуючи їх ключі, розбір і валідацію.
     * Повністю відсутні ключі не є помилкою; неповні або пошкоджені значення діагностуються та замінюються типовими.
     *
     * @param databasePath шлях до бази даних, що додається до діагностичного повідомлення.
     * @return збережений коректний стан камери або типовий стан за відсутніх чи пошкоджених даних.
     * @throws PersistenceException якщо SQLite не може прочитати налаштування.
     */
    public CameraState loadCameraState(Path databasePath) {
        try {
            String zoomValue = load(CAMERA_ZOOM_KEY);
            String panXValue = load(CAMERA_PAN_X_KEY);
            String panYValue = load(CAMERA_PAN_Y_KEY);

            if (zoomValue == null && panXValue == null && panYValue == null) {
                return CameraState.defaultState();
            }
            if (zoomValue == null || panXValue == null || panYValue == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Неповний збережений стан камери в SQLite; використано типовий стан. Database: {0}",
                        databasePath
                );
                return CameraState.defaultState();
            }

            try {
                return new CameraState(
                        Double.parseDouble(zoomValue),
                        Double.parseDouble(panXValue),
                        Double.parseDouble(panYValue)
                );
            } catch (IllegalArgumentException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "Некоректний збережений стан камери в SQLite; використано типовий стан. Database: "
                                + databasePath,
                        exception
                );
                return CameraState.defaultState();
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Не вдалося завантажити стан камери з SQLite. Database: " + databasePath,
                    exception
            );
        }
    }

    /**
     * Зберігає всі складові камери через цей самий механізм налаштувань і поточне з'єднання SQLite.
     * Виклик не створює окремої транзакції, тому запис входить до транзакції репозиторію карти.
     *
     * @param cameraState перевірений стан камери, який потрібно зберегти.
     * @throws SQLException якщо будь-який із записів не вдався; зовнішній репозиторій відповідає за відкат.
     * @throws NullPointerException якщо стан камери не задано.
     */
    public void saveCameraState(CameraState cameraState) throws SQLException {
        if (cameraState == null) {
            throw new NullPointerException("cameraState");
        }
        write(CAMERA_ZOOM_KEY, Double.toString(cameraState.zoom()));
        write(CAMERA_PAN_X_KEY, Double.toString(cameraState.panX()));
        write(CAMERA_PAN_Y_KEY, Double.toString(cameraState.panY()));
    }

    /**
     * Завантажує тривалість такту симуляції або повертає резервне значення для відсутніх і некоректних даних.
     *
     * @param fallbackMillis резервна тривалість такту.
     * @return збережена додатна скінченна тривалість такту або резервне значення.
     */
    public double loadSimulationTickMillis(double fallbackMillis) {
        try {
            String value = load(SIMULATION_TICK_MILLIS_KEY);
            if (value == null) {
                return fallbackMillis;
            }

            double millis = Double.parseDouble(value);
            return Double.isFinite(millis) && millis > 0.0 ? millis : fallbackMillis;
        } catch (NumberFormatException exception) {
            return fallbackMillis;
        } catch (SQLException exception) {
            throw new PersistenceException("Не вдалося завантажити налаштування SQLite.", exception);
        }
    }

    /**
     * Зберігає тривалість такту симуляції в таблиці налаштувань активного проєкту.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    public void saveSimulationTickMillis(double millis) {
        try {
            write(SIMULATION_TICK_MILLIS_KEY, Double.toString(millis));
        } catch (SQLException exception) {
            throw new PersistenceException("Не вдалося зберегти швидкість такту.", exception);
        }
    }
}
