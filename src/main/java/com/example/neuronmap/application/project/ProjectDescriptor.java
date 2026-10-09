package com.example.neuronmap.application.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Повертає результат операції «проєкт».
 *
 * @param id ідентифікатор обʼєкта.
 *
 * @param name назва або текстове імʼя обʼєкта.
 *
 * @param databasePath значення, що визначає база даних шлях для цієї операції.
 *
 * @param modifiedAt значення, що визначає відповідну операцію для цієї операції.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Описує збережений проєкт: його назву, розташування та повʼязані метадані.
 */
public record ProjectDescriptor(
        String id,
        String name,
        Path databasePath,
        Instant modifiedAt
) {
    public ProjectDescriptor {
        if (id == null || id.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("project id must not be blank");
        }
        if (name == null || name.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("project name must not be blank");
        }
        databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isPersisted() {
        return modifiedAt != null;
    }
}
