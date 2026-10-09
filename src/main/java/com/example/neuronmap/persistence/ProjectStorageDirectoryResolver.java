package com.example.neuronmap.persistence;

import java.nio.file.Path;

/**
 * Визначає каталог, у якому застосунок зберігає файли проєктів.
 */
public final class ProjectStorageDirectoryResolver {

    private static final String DOCUMENTS_DIRECTORY = "Documents";
    private static final String APPLICATION_DIRECTORY = "NeuronMap";

    private ProjectStorageDirectoryResolver() {
    }

    /**
     * Повертає каталог, у якому мають зберігатися окремі проєкти.
     *
     * @return каталог, у якому мають зберігатися окремі проєкти.
     */
    public static Path resolve() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {

            throw new IllegalStateException("User home directory is unavailable");
        }

        return Path.of(userHome)
                .resolve(DOCUMENTS_DIRECTORY)
                .resolve(APPLICATION_DIRECTORY)
                .toAbsolutePath()
                .normalize();
    }
}
