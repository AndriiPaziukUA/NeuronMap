package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.PersistenceException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Реалізує постійне сховище карти на SQLite та керує транзакціями, параметрами камери й налаштуваннями симуляції.
 */
public final class SqliteMapRepository implements MapRepository {

    private static final Logger LOGGER = Logger.getLogger(SqliteMapRepository.class.getName());

    private final Path databasePath;
    private final Connection connection;
    private final SqliteSettingsStore settings;
    private final SqliteMapLoader loader;
    private final SqliteMapWriter writer;
    private boolean closed;

    /**
     * Відкриває базу даних проєкту, виконує міграції схеми та готує компоненти читання й запису.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @throws IllegalArgumentException якщо шлях не задано.
     * @throws PersistenceException якщо не вдалося відкрити базу даних або виконати міграції.
     */
    public SqliteMapRepository(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }

        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.connection = SqliteConnectionFactory.open(this.databasePath);
        try {
            SqliteSchema.migrate(this.connection);
        } catch (RuntimeException exception) {
            closeAfterInitializationFailure(exception);
            throw exception;
        }

        this.settings = new SqliteSettingsStore(this.connection);
        this.loader = new SqliteMapLoader(this.connection);
        this.writer = new SqliteMapWriter(this.connection, settings);
    }

    /**
     * Перевіряє, чи залишається репозиторій відкритим для роботи з постійними даними.
     *
     * @return {@code true}, якщо репозиторій ще не закрито; інакше {@code false}.
     */
    @Override
    public boolean isPersistent() {
        return !closed;
    }

    /**
     * Повертає нормалізований шлях до файла бази даних репозиторію.
     *
     * @return абсолютний шлях до файла бази даних.
     */
    @Override
    public Path databasePath() {
        return databasePath;
    }

    /**
     * Завантажує збережену тривалість такту симуляції або повертає передане резервне значення.
     *
     * @param fallbackMillis резервна тривалість такту, якщо налаштування відсутнє або некоректне.
     * @return збережена додатна скінченна тривалість такту або {@code fallbackMillis}.
     */
    @Override
    public double loadSimulationTickMillis(double fallbackMillis) {
        ensureOpen();
        return settings.loadSimulationTickMillis(fallbackMillis);
    }

    /**
     * Зберігає тривалість такту симуляції та оновлює час зміни файла бази даних.
     *
     * @param millis тривалість такту в мілісекундах.
     */
    @Override
    public synchronized void saveSimulationTickMillis(double millis) {
        ensureOpen();
        settings.saveSimulationTickMillis(millis);
        touchDatabaseFile();
    }

    /**
     * Завантажує параметри камери. Відсутні налаштування замінює типовим станом без попередження,
     * а пошкоджені або неповні збережені значення реєструє та відновлює до типового стану.
     *
     * @return коректний збережений стан камери або типовий стан, якщо налаштування відсутні чи пошкоджені.
     * @throws IllegalStateException якщо репозиторій закрито.
     * @throws PersistenceException якщо SQLite не може прочитати налаштування.
     */
    @Override
    public CameraState loadCameraState() {
        ensureOpen();
        try {
            String zoomValue = settings.load("zoom");
            String panXValue = settings.load("panX");
            String panYValue = settings.load("panY");

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
            throw failure("Не вдалося завантажити стан камери з SQLite.", exception);
        }
    }

    /**
     * Завантажує вміст бази даних у передану модель карти.
     *
     * @param model модель карти нейронів, яку потрібно заповнити даними.
     * @throws IllegalStateException якщо репозиторій закрито.
     * @throws PersistenceException якщо не вдалося прочитати карту з SQLite.
     */
    @Override
    public void loadInto(NeuronMapModel model) {
        ensureOpen();
        try {
            loader.loadInto(model);
        } catch (SQLException exception) {
            throw failure("Не вдалося завантажити карту з SQLite.", exception);
        }
    }

    /**
     * Атомарно зберігає модель карти й стан камери, зберігаючи первісну помилку та діагностику збоїв відкату.
     *
     * @param model модель карти, яку потрібно зберегти.
     * @param cameraState стан камери, який потрібно зберегти разом із картою.
     * @throws IllegalStateException якщо репозиторій закрито.
     * @throws PersistenceException якщо не вдалося зберегти дані або відновити стан транзакції.
     */
    @Override
    public synchronized void save(NeuronMapModel model, CameraState cameraState) {
        ensureOpen();

        boolean transactionMayBeActive = false;
        boolean committed = false;
        RuntimeException operationFailure = null;

        try {
            connection.setAutoCommit(false);
            transactionMayBeActive = true;
            writer.save(model, cameraState);
            connection.commit();
            committed = true;
        } catch (SQLException exception) {
            operationFailure = failure("Не вдалося зберегти карту в SQLite.", exception);
        } catch (RuntimeException exception) {
            operationFailure = exception;
        }

        if (operationFailure != null && transactionMayBeActive) {
            operationFailure = rollbackAfterFailure(operationFailure);
            if (closed) {
                throw operationFailure;
            }
        }

        if (transactionMayBeActive) {
            operationFailure = restoreAutoCommit(operationFailure);
            if (closed) {
                throw operationFailure;
            }
        }

        if (operationFailure != null) {
            throw operationFailure;
        }

        if (committed) {
            touchDatabaseFile();
        }
    }

    /**
     * Закриває з’єднання SQLite та звільняє ресурси репозиторію; повторне закриття не змінює результат.
     *
     * @throws PersistenceException якщо SQLite не вдалося закрити з’єднання.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException exception) {
            throw failure("Не вдалося закрити SQLite.", exception);
        }
    }

    /**
     * Оновлює часову мітку файла бази даних після успішного збереження.
     *
     * @throws PersistenceException якщо неможливо оновити часову мітку файла.
     */
    private void touchDatabaseFile() {
        try {
            Files.setLastModifiedTime(databasePath, FileTime.from(Instant.now()));
        } catch (java.io.IOException exception) {
            throw failure("Не вдалося оновити час зміни SQLite.", exception);
        }
    }

    /**
     * Забороняє доступ до з’єднання після закриття або коли його стан став ненадійним.
     *
     * @throws IllegalStateException якщо репозиторій закрито.
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite repository is closed");
        }
    }

    /**
     * Виконує відкат після невдалої операції; якщо відкат теж не вдається, закриває репозиторій.
     *
     * @param operationFailure первісна помилка операції.
     * @return первісна помилка з доданою інформацією про невдалий відкат, якщо він не вдався.
     */
    private RuntimeException rollbackAfterFailure(RuntimeException operationFailure) {
        try {
            connection.rollback();
            return operationFailure;
        } catch (SQLException rollbackException) {
            operationFailure.addSuppressed(
                    failure("Не вдалося виконати відкат транзакції SQLite.", rollbackException)
            );
            closeAfterTransactionStateFailure(operationFailure);
            return operationFailure;
        }
    }

    /**
     * Відновлює автоматичне підтвердження транзакцій, не замінюючи первісної помилки.
     *
     * @param operationFailure помилка операції або {@code null}, якщо вона завершилася успішно.
     * @return первісна помилка або помилка відновлення режиму транзакцій.
     */
    private RuntimeException restoreAutoCommit(RuntimeException operationFailure) {
        try {
            connection.setAutoCommit(true);
            return operationFailure;
        } catch (SQLException restoreException) {
            RuntimeException restoreFailure = failure(
                    "Не вдалося відновити режим транзакції SQLite.",
                    restoreException
            );
            if (operationFailure == null) {
                operationFailure = restoreFailure;
            } else {
                operationFailure.addSuppressed(restoreFailure);
            }
            closeAfterTransactionStateFailure(operationFailure);
            return operationFailure;
        }
    }

    /**
     * Позначає репозиторій закритим і намагається звільнити з’єднання, стан транзакції якого невідомий.
     *
     * @param primaryFailure помилка, яку потрібно зберегти основною.
     */
    private void closeAfterTransactionStateFailure(RuntimeException primaryFailure) {
        closed = true;
        try {
            connection.close();
        } catch (SQLException closeException) {
            primaryFailure.addSuppressed(
                    failure("Не вдалося закрити з’єднання після помилки транзакції SQLite.", closeException)
            );
        }
    }

    /**
     * Закриває з’єднання, якщо під час створення репозиторію не вдалося виконати міграції схеми.
     *
     * @param initializationFailure виняток, через який ініціалізація завершилася невдало.
     */
    private void closeAfterInitializationFailure(RuntimeException initializationFailure) {
        try {
            connection.close();
        } catch (SQLException closeException) {
            initializationFailure.addSuppressed(closeException);
        }
    }

    /**
     * Створює виняток сховища з контекстом шляху до бази даних і першопричиною помилки.
     *
     * @param message опис операції, що завершилася невдало.
     * @param cause першопричина помилки.
     * @return виняток сховища з додатковим контекстом.
     */
    private PersistenceException failure(String message, Throwable cause) {
        return new PersistenceException(message + " Database: " + databasePath, cause);
    }
}
