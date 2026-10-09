package com.example.neuronmap.persistence;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Відстежує зміни каталогів проєктів і повідомляє слухача, коли список чи вміст проєктів може потребувати оновлення.
 */
public final class ProjectDirectoryWatcher implements AutoCloseable {

    private final Path rootDirectory;
    private final Runnable changeListener;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();

    private WatchService watchService;
    private Thread watcherThread;

    /**
     * Створює екземпляр ProjectDirectoryWatcher та зберігає передані залежності, потрібні для його роботи.
     *
     * @param rootDirectory кореневий каталог сховища проєктів.
     * @param changeListener callback, який викликається після зміни каталогу.
     */
    public ProjectDirectoryWatcher(
            Path rootDirectory,
            Runnable changeListener
    ) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath()
                .normalize();
        this.changeListener = Objects.requireNonNull(
                changeListener,
                "changeListener"
        );
    }

    /**
     * Запускає спостереження за каталогами проєктів.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        try {
            Files.createDirectories(rootDirectory);
            watchService = rootDirectory.getFileSystem().newWatchService();
            registerDirectory(rootDirectory);
            registerExistingProjectDirectories();
        } catch (IOException exception) {
            close();

            throw new PersistenceException(
                    "Не вдалося запустити спостереження за проєктами.",
                    exception
            );
        }

        running.set(true);
        watcherThread = new Thread(
                this::watchLoop,
                "NeuronMap-project-directory-watcher"
        );
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    /**
     * Зупиняє спостереження та звільняє ресурси; повторний виклик безпечний.
     */
    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            closeWatchServiceQuietly();
            return;
        }

        closeWatchServiceQuietly();
        watcherThread = null;
        watchedDirectories.clear();
    }

    /**
     * Обробляє події файлової системи, поки спостерігач працює.
     */
    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                if (running.get()) {
                    notifyChange();
                }
                return;
            }

            boolean changed = false;
            Path watchedDirectory = watchedDirectories.get(key);
            if (watchedDirectory == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    changed = true;
                    continue;
                }

                changed = true;
                if (watchedDirectory.equals(rootDirectory)
                        && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    Path created = watchedDirectory.resolve(
                            (Path) event.context()
                    );
                    registerDirectoryIfNeeded(created);
                }
            }

            if (!key.reset()) {
                watchedDirectories.remove(key);
            }

            if (changed) {
                notifyChange();
            }
        }
    }

    /**
     * Реєструє наявні каталоги проєктів у файловому спостерігачі, щоб відстежувати наступні зміни.
     */
    private void registerExistingProjectDirectories() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirectory)) {
            for (Path child : stream) {
                registerDirectoryIfNeeded(child);
            }
        }
    }

    /**
     * Реєструє каталог у спостерігачі лише тоді, коли його ще не зареєстровано.
     *
     * @param directory каталог, який потрібно обробити.
     */
    private synchronized void registerDirectoryIfNeeded(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try {
            registerDirectory(directory);
        } catch (IOException exception) {
            notifyChange();
        }
    }

    /**
     * Реєструє каталог у WatchService для отримання подій створення, видалення або зміни файлів.
     *
     * @param directory каталог, який потрібно обробити.
     */
    private synchronized void registerDirectory(Path directory)
            throws IOException {
        if (watchService == null) {
            return;
        }

        WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchedDirectories.put(key, directory);
    }

    /**
     * Викликає слухача змін, щоб каталог проєктів було перечитано.
     */
    private void notifyChange() {
        try {
            changeListener.run();
        } catch (RuntimeException ignored) {

        }
    }

    /**
     * Закриває WatchService, не перериваючи завершення спостерігача через додаткову помилку закриття.
     */
    private synchronized void closeWatchServiceQuietly() {
        if (watchService == null) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException ignored) {

        }
        watchService = null;
    }
}
