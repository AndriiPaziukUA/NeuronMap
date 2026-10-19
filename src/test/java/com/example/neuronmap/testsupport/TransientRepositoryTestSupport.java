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
     * Створює відкладений репозиторій і визначає довірений корінь тестового сховища з батьківської структури шляху.
     * Межі володіння охоплюють лише каталог конкретного проєкту, а не всі каталоги файлової системи.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @return тимчасовий репозиторій із налаштованим постачальником постійного делегата.
     * @throws NullPointerException якщо шлях не задано.
     * @throws IllegalArgumentException якщо шлях не містить каталогу проєкту з батьківським коренем.
     */
    public static MapRepository create(Path databasePath) {
        Path normalizedPath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        Path ownershipRoot = resolveOwnershipRoot(normalizedPath);
        Path fileName = normalizedPath.getFileName();
        String name = fileName == null ? "Temporary project" : fileName.toString();
        ProjectDescriptor project = new ProjectDescriptor(
                normalizedPath.toString(),
                name,
                normalizedPath,
                null
        );
        return new DefaultMapRepositoryFactory(ownershipRoot).createTransientProjectRepository(project);
    }

    /**
     * Визначає каталог, який є батьком безпосереднього каталогу проєкту.
     *
     * @param databasePath абсолютний нормалізований шлях до бази даних.
     * @return довірений корінь, безпосереднім дочірнім каталогом якого є каталог проєкту.
     * @throws IllegalArgumentException якщо для шляху неможливо визначити обидва рівні каталогів.
     */
    private static Path resolveOwnershipRoot(Path databasePath) {
        Path projectDirectory = databasePath.getParent();
        Path ownershipRoot = projectDirectory == null ? null : projectDirectory.getParent();
        if (ownershipRoot == null) {
            throw new IllegalArgumentException(
                    "Project database must be nested in a project directory with an ownership root"
            );
        }
        return ownershipRoot;
    }
}
