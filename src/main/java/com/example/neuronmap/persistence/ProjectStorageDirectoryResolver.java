package com.example.neuronmap.persistence;

import java.nio.file.Path;

/**
 * Визначає кореневий каталог, у якому розміщуються папки проєктів.
 */
public final class ProjectStorageDirectoryResolver {

    private static final String DOCUMENTS_DIRECTORY = "Documents";
    private static final String APPLICATION_DIRECTORY = "NeuronMap";

    /**
     * Повертає результат операції «проєкт каталог».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private ProjectStorageDirectoryResolver() {
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    public static Path resolve() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("User home directory is unavailable");
        }

        return Path.of(userHome)
                .resolve(DOCUMENTS_DIRECTORY)
                .resolve(APPLICATION_DIRECTORY)
                .toAbsolutePath()
                .normalize();
    }
}
