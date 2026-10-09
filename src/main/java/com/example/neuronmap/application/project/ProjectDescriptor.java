package com.example.neuronmap.application.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Описує проєкт у каталозі застосунку та вказує на його файл даних.
 * @param id унікальний ідентифікатор проєкту.
 * @param name назва, яку показують у списку проєктів.
 * @param databasePath абсолютний шлях до файлу бази даних проєкту.
 * @param modifiedAt час останньої зміни збереженого проєкту; null для незбереженого проєкту.
 */
public record ProjectDescriptor(
        String id,
        String name,
        Path databasePath,
        Instant modifiedAt
) {
    /**
     * Створює опис проєкту та нормалізує шлях до його файла бази даних.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param name назва, яку потрібно перевірити або зберегти.
     * @param databasePath шлях до файлу бази даних проєкту.
     * @param modifiedAt час останньої зміни збереженого проєкту або null, якщо проєкт ще не збережено.
     */
    public ProjectDescriptor {
        if (id == null || id.isBlank()) {

            throw new IllegalArgumentException("project id must not be blank");
        }
        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException("project name must not be blank");
        }
        databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    public boolean isPersisted() {
        return modifiedAt != null;
    }
}
