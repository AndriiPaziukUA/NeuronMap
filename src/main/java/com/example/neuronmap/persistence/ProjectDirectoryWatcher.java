package com.example.neuronmap.persistence;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Відстежує зміни кореневого каталогу проєктів і самих каталогів проєктів та повідомляє слухача про потребу оновлення.
 * Спостерігач працює у фоновому потоці, а помилки слухача не припиняють подальше отримання файлових подій.
 */
public final class ProjectDirectoryWatcher implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(ProjectDirectoryWatcher.class.getName());

    private final Path rootDirectory;
    private final Runnable changeListener;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watcherThread;

    /**
     * Створює спостерігач для каталогу проєктів і визначає дію, яку потрібно викликати після файлових змін.
     *
     * @param rootDirectory кореневий каталог сховища проєктів.
     * @param changeListener слухач, який запускає оновлення даних каталогу.
     * @throws NullPointerException якщо не задано каталог або слухача.
     */
    public ProjectDirectoryWatcher(
            Path rootDirectory,
            Runnable changeListener
    ) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath()
                .normalize();
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    /**
     * Готує файловий спостерігач, реєструє корінь і наявні каталоги проєктів та запускає фоновий потік.
     * Повторний виклик під час активного спостереження не запускає додаткового потоку.
     *
     * @throws PersistenceException якщо файловий спостерігач не вдалося підготувати.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        try {
            Files.createDirectories(rootDirectory);
            watchService = rootDirectory.getFileSystem().newWatchService();
            WatchService service = watchService;
            registerDirectory(rootDirectory, service);
            registerExistingProjectDirectories(service);

            Thread thread = new Thread(
                    () -> watchLoop(service),
                    "NeuronMap-project-directory-watcher"
            );
            thread.setDaemon(true);
            watcherThread = thread;
            running.set(true);
            thread.start();
        } catch (IOException exception) {
            cleanupAfterFailedStart();
            throw new PersistenceException(
                    "Не вдалося запустити спостереження за проєктами.",
                    exception
            );
        } catch (RuntimeException exception) {
            cleanupAfterFailedStart();
            throw exception;
        }
    }

    /**
     * Зупиняє фоновий потік і звільняє файловий спостерігач; повторний виклик безпечний.
     * Закритий екземпляр можна запустити повторно через {@link #start()}.
     */
    @Override
    public synchronized void close() {
        running.set(false);
        WatchService service = watchService;
        watchService = null;
        watchedDirectories.clear();

        Thread thread = watcherThread;
        watcherThread = null;
        closeWatchService(service);
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Читає й обробляє файлові події для конкретного екземпляра WatchService.
     * Завершення старого потоку не змінює стан нового, якщо спостерігач було перезапущено.
     *
     * @param service файловий спостерігач, яким користується цей потік.
     */
    private void watchLoop(WatchService service) {
        try {
            while (running.get()) {
                WatchKey key;
                try {
                    key = service.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ClosedWatchServiceException exception) {
                    return;
                }

                if (!running.get()) {
                    return;
                }

                Path watchedDirectory = watchedDirectories.get(key);
                if (watchedDirectory == null) {
                    if (!key.reset()) {
                        watchedDirectories.remove(key);
                    }
                    continue;
                }

                boolean changed = processEvents(key, watchedDirectory, service);
                if (!key.reset()) {
                    watchedDirectories.remove(key);
                    changed = true;
                }

                if (changed) {
                    notifyChange();
                }
            }
        } catch (RuntimeException exception) {
            if (running.get()) {
                LOGGER.log(Level.SEVERE, "Помилка під час обробки подій каталогу проєктів.", exception);
            }
        } finally {
            finishWatcherThread(service);
        }
    }

    /**
     * Обробляє одну порцію подій і додає до спостереження нові каталоги, створені в корені сховища.
     *
     * @param key ключ із файловими подіями.
     * @param watchedDirectory каталог, для якого надійшли події.
     * @param service файловий спостерігач поточного потоку.
     * @return {@code true}, якщо надійшла подія, що потребує оновлення каталогу.
     */
    private boolean processEvents(WatchKey key, Path watchedDirectory, WatchService service) {
        boolean changed = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                changed = true;
                continue;
            }

            changed = true;
            if (watchedDirectory.equals(rootDirectory)
                    && kind == StandardWatchEventKinds.ENTRY_CREATE
                    && event.context() instanceof Path relativePath) {
                registerDirectoryIfNeeded(watchedDirectory.resolve(relativePath), service);
            }
        }
        return changed;
    }

    /**
     * Реєструє каталоги, які вже існують у корені, щоб отримувати зміни їхніх файлів.
     *
     * @param service файловий спостерігач, до якого потрібно додати каталоги.
     * @throws IOException якщо читання кореневого каталогу не вдалося.
     */
    private void registerExistingProjectDirectories(WatchService service) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirectory)) {
            for (Path child : stream) {
                registerDirectoryIfNeeded(child, service);
            }
        }
    }

    /**
     * Додає каталог до спостереження, якщо він існує та спостерігач ще активний.
     * Помилка реєстрації записується в журнал, а основний цикл продовжує працювати.
     *
     * @param directory каталог, який потрібно зареєструвати.
     * @param service файловий спостерігач поточного потоку.
     */
    private synchronized void registerDirectoryIfNeeded(Path directory, WatchService service) {
        if (watchService != service || !Files.isDirectory(directory)) {
            return;
        }

        try {
            registerDirectory(directory, service);
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Не вдалося відстежувати каталог " + directory, exception);
            notifyChange();
        }
    }

    /**
     * Реєструє каталог для подій створення, видалення й зміни його безпосередніх елементів.
     *
     * @param directory каталог, який потрібно зареєструвати.
     * @param service файловий спостерігач, який прийматиме події.
     * @throws IOException якщо реєстрація каталогу не вдалася.
     */
    private void registerDirectory(Path directory, WatchService service) throws IOException {
        WatchKey key = directory.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchedDirectories.put(key, directory);
    }

    /**
     * Сповіщає слухача про файлову зміну. Помилка слухача реєструється, але не припиняє спостереження.
     */
    private void notifyChange() {
        try {
            changeListener.run();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Слухач змін каталогу проєктів завершився з помилкою.", exception);
        }
    }

    /**
     * Завершує фоновий потік і закриває його спостерігач, лише якщо він усе ще є поточним.
     *
     * @param service файловий спостерігач, із яким працював потік.
     */
    private void finishWatcherThread(WatchService service) {
        synchronized (this) {
            if (watcherThread != Thread.currentThread()) {
                return;
            }

            running.set(false);
            watcherThread = null;
            if (watchService == service) {
                watchService = null;
                watchedDirectories.clear();
                closeWatchService(service);
            }
        }
    }

    /**
     * Прибирає частково створені ресурси, якщо запуск спостерігача завершився помилкою.
     */
    private void cleanupAfterFailedStart() {
        running.set(false);
        Thread thread = watcherThread;
        watcherThread = null;
        WatchService service = watchService;
        watchService = null;
        watchedDirectories.clear();
        closeWatchService(service);
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Закриває WatchService та реєструє помилку закриття, не перешкоджаючи звільненню інших ресурсів.
     *
     * @param service файловий спостерігач, який потрібно закрити; {@code null} ігнорується.
     */
    private static void closeWatchService(WatchService service) {
        if (service == null) {
            return;
        }

        try {
            service.close();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Не вдалося закрити файловий спостерігач каталогу проєктів.", exception);
        }
    }
}
