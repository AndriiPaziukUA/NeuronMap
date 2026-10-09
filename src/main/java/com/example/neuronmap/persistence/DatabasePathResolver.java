package com.example.neuronmap.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Визначає шлях до файлу бази даних для потрібного проєкту.
 */
public final class DatabasePathResolver {

    private static final String DATABASE_NAME = "neuronmap.db";

    /**
     * Повертає результат операції «база даних шлях».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private DatabasePathResolver() {
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @return шлях до відповідного файлу або каталогу.
     */
    public static Path resolve() {
        Path workingDirectory = Path.of(
                        System.getProperty("user.dir")
                )
                .toAbsolutePath()
                .normalize();

        Path current = workingDirectory;

        while (current != null) {
            if (Files.isRegularFile(
                    current.resolve("pom.xml")
            )) {
                return current.resolve(DATABASE_NAME);
            }

            current = current.getParent();
        }

        return workingDirectory.resolve(
                DATABASE_NAME
        );
    }
}
