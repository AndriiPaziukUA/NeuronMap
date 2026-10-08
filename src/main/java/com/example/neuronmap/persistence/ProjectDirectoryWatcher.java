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

/** Watches the project storage tree and reports filesystem changes. */
public final class ProjectDirectoryWatcher implements AutoCloseable {

    private final Path rootDirectory;
    private final Runnable changeListener;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();

    private WatchService watchService;
    private Thread watcherThread;

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

    private void registerExistingProjectDirectories() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirectory)) {
            for (Path child : stream) {
                registerDirectoryIfNeeded(child);
            }
        }
    }

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

    private void notifyChange() {
        try {
            changeListener.run();
        } catch (RuntimeException ignored) {
            // The watcher must stay alive when a UI refresh fails.
        }
    }

    private synchronized void closeWatchServiceQuietly() {
        if (watchService == null) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException ignored) {
            // Shutdown is best-effort.
        }
        watchService = null;
    }
}
