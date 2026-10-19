package com.example.neuronmap.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Надає спільні вузькі операції над деревами каталогів, якими володіє сховище проєктів.
 * Обидві операції працюють лише з безпосереднім дочірнім каталогом довіреного кореня та не переходять за симлінками.
 */
public final class FileTreeOperations {

    /** Забороняє створення екземплярів класу зі статичними файловими операціями. */
    private FileTreeOperations() {
    }

    /**
     * Видаляє вміст належного сховищу каталогу проєкту, залишаючи сам каталог на місці.
     *
     * @param ownershipRoot довірений корінь сховища, який містить каталог проєкту.
     * @param ownedDirectory каталог проєкту, вміст якого належить сховищу й може бути очищений.
     * @throws IOException якщо каталог або його вміст неможливо перевірити чи видалити.
     * @throws IllegalArgumentException якщо каталог не є безпосереднім дочірнім каталогом кореня володіння.
     * @throws NullPointerException якщо корінь володіння або каталог не задано.
     */
    public static void clearOwnedDirectoryContents(Path ownershipRoot, Path ownedDirectory) throws IOException {
        Path directory = validateOwnedDirectory(ownershipRoot, ownedDirectory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned project directory is not a real directory: " + directory);
        }

        for (Path path : descendantsInDeletionOrder(directory)) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Видаляє належне сховищу дерево проєкту разом із кореневим каталогом.
     *
     * @param ownershipRoot довірений корінь сховища проєктів.
     * @param ownedDirectory безпосередній дочірній каталог кореня, який повністю видаляється.
     * @throws IOException якщо дерево каталогу неможливо перевірити чи видалити.
     * @throws IllegalArgumentException якщо каталог не є безпосереднім дочірнім каталогом кореня володіння.
     * @throws NullPointerException якщо корінь володіння або каталог не задано.
     */
    public static void deleteOwnedDirectoryTree(Path ownershipRoot, Path ownedDirectory) throws IOException {
        Path directory = validateOwnedDirectory(ownershipRoot, ownedDirectory);
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Owned project directory must not be a symbolic link: " + directory);
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned project path is not a real directory: " + directory);
        }

        for (Path path : descendantsInDeletionOrder(directory)) {
            Files.deleteIfExists(path);
        }
        Files.deleteIfExists(directory);
    }

    /**
     * Перевіряє належність каталогу кореню сховища та відхиляє символічні посилання в межах довіреної межі.
     *
     * @param ownershipRoot корінь, у межах якого виконується видалення.
     * @param ownedDirectory каталог, над яким виконується операція.
     * @return нормалізований абсолютний шлях до каталогу.
     * @throws IOException якщо корінь володіння є симлінком або не є каталогом.
     * @throws IllegalArgumentException якщо шляхи некоректні або каталог лежить поза коренем володіння.
     */
    private static Path validateOwnedDirectory(Path ownershipRoot, Path ownedDirectory) throws IOException {
        Path root = Objects.requireNonNull(ownershipRoot, "ownershipRoot").toAbsolutePath().normalize();
        Path directory = Objects.requireNonNull(ownedDirectory, "ownedDirectory").toAbsolutePath().normalize();
        if (root.equals(directory) || !root.equals(directory.getParent())) {
            throw new IllegalArgumentException("Project directory is outside the ownership root");
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Ownership root must not be a symbolic link: " + root);
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ownership root is not a directory: " + root);
        }
        return directory;
    }

    /**
     * Обходить лише сам каталог і його дочірні елементи, не розкриваючи символічні посилання.
     *
     * @param directory перевірений каталог проєкту.
     * @return шляхи у зворотному порядку, придатному для безпечного видалення вкладених елементів.
     * @throws IOException якщо файлове дерево неможливо обійти.
     */
    private static List<Path> descendantsInDeletionOrder(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(path -> !path.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
