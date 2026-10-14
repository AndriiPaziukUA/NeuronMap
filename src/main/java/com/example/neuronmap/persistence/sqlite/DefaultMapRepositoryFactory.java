package com.example.neuronmap.persistence.sqlite;

import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.map.TransientProjectRepository;
import com.example.neuronmap.project.ProjectDescriptor;

import java.util.Objects;

/**
 * Створює файлові репозиторії карти для тимчасових і збережених проєктів.
 */
public final class DefaultMapRepositoryFactory implements MapRepositoryFactory {

    /**
     * Створює репозиторій, який матеріалізує тимчасовий проєкт лише після появи даних для збереження.
     *
     * @param project опис тимчасового проєкту.
     * @return репозиторій тимчасового проєкту.
     */
    @Override
    public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
        Objects.requireNonNull(project, "project");
        return new TransientProjectRepository(project.databasePath());
    }

    /**
     * Створює постійний SQLite-репозиторій для збереженого проєкту.
     *
     * @param project опис збереженого проєкту.
     * @return SQLite-репозиторій проєкту.
     */
    @Override
    public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
        Objects.requireNonNull(project, "project");
        return new SqliteMapRepository(project.databasePath());
    }
}
