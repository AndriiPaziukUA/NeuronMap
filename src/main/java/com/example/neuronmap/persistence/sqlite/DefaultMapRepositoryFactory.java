package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.persistence.project.ProjectStorageDirectoryResolver;
import com.example.neuronmap.project.ProjectDescriptor;

import java.nio.file.Path;
import java.util.Objects;

/** Створює репозиторії карти в межах довіреного каталогу зберігання проєктів. */
public final class DefaultMapRepositoryFactory implements MapRepositoryFactory {

    private final Path storageDirectory;

    /** Створює фабрику зі стандартним коренем сховища проєктів застосунку. */
    public DefaultMapRepositoryFactory() {
        this(ProjectStorageDirectoryResolver.resolve());
    }

    /**
     * Створює фабрику, обмежуючи матеріалізацію тимчасових проєктів заданим коренем сховища.
     *
     * @param storageDirectory довірений каталог, усередині якого зберігаються каталоги проєктів.
     * @throws NullPointerException якщо каталог не задано.
     */
    public DefaultMapRepositoryFactory(Path storageDirectory) {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Створює репозиторій, який матеріалізує тимчасовий проєкт лише після появи даних для збереження.
     * Переданий корінь володіння обмежує очищення вмістом безпосереднього дочірнього каталогу проєкту.
     * Під час матеріалізації, якщо файл бази ще не існує, наявний вміст цього каталогу видаляється;
     * сам кореневий каталог сховища та його сусідні каталоги не видаляються.
     *
     * @param project опис тимчасового проєкту.
     * @return репозиторій тимчасового проєкту.
     * @throws NullPointerException якщо опис проєкту не задано.
     * @throws IllegalArgumentException якщо каталог проєкту розташовано поза коренем сховища.
     */
    @Override
    public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
        Objects.requireNonNull(project, "project");
        return new TransientProjectRepository(
                project.databasePath(),
                storageDirectory,
                () -> createPersistentDelegate(project)
        );
    }

    /**
     * Створює постійний SQLite-репозиторій для збереженого проєкту.
     * Цей самий шлях створення використовується як делегат після матеріалізації тимчасового проєкту.
     *
     * @param project опис збереженого проєкту.
     * @return SQLite-репозиторій проєкту.
     */
    @Override
    public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
        return createSqliteRepository(project);
    }

    /**
     * Створює постійний SQLite-делегат для матеріалізованого тимчасового проєкту.
     *
     * @param project опис проєкту, який матеріалізується.
     * @return SQLite-репозиторій, що обслуговуватиме подальші операції збереження.
     */
    @Override
    public MapRepository createPersistentDelegate(ProjectDescriptor project) {
        return createSqliteRepository(project);
    }

    /**
     * Централізує створення SQLite-репозиторіїв, щоб відкриття збереженого проєкту й матеріалізація
     * використовували однаковий механізм ініціалізації сховища.
     *
     * @param project опис проєкту, для якого створюється репозиторій.
     * @return новий SQLite-репозиторій.
     * @throws NullPointerException якщо опис проєкту не задано.
     */
    private MapRepository createSqliteRepository(ProjectDescriptor project) {
        Objects.requireNonNull(project, "project");
        return new SqliteMapRepository(project.databasePath());
    }
}
