package com.example.neuronmap.testsupport;

import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.sqlite.DefaultMapRepositoryFactory;
import com.example.neuronmap.project.ProjectDescriptor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Надає тестам тимчасові репозиторії через фабрику застосунку, зберігаючи єдиний шлях створення делегата.
 */
public final class TransientRepositoryTestSupport {

    /**
     * Забороняє створення екземплярів тестового допоміжного класу.
     */
    private TransientRepositoryTestSupport() {
    }

    /**
     * Створює тимчасовий репозиторій для заданого шляху через стандартну фабрику репозиторіїв.
     * Постійний SQLite-репозиторій залишається відкладеним до матеріалізації проєкту.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @return тимчасовий репозиторій із налаштованим постачальником постійного делегата.
     * @throws NullPointerException якщо шлях не задано.
     */
    public static MapRepository create(Path databasePath) {
        Path normalizedPath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        Path fileName = normalizedPath.getFileName();
        String name = fileName == null ? "Temporary project" : fileName.toString();
        ProjectDescriptor project = new ProjectDescriptor(
                normalizedPath.toString(),
                name,
                normalizedPath,
                null
        );
        return new DefaultMapRepositoryFactory().createTransientProjectRepository(project);
    }
}
