package com.example.neuronmap.persistence;

import com.example.neuronmap.project.ProjectDescriptor;

/**
 * Визначає створення репозиторіїв для тимчасових і збережених проєктів.
 */
public interface MapRepositoryFactory {

    /**
     * Створює репозиторій для нового тимчасового проєкту.
     *
     * @param project опис проєкту, який потрібно відкрити як тимчасовий.
     * @return репозиторій тимчасового проєкту.
     */
    MapRepository createTransientProjectRepository(ProjectDescriptor project);

    /**
     * Створює репозиторій для наявного збереженого проєкту.
     *
     * @param project опис збереженого проєкту, який потрібно відкрити.
     * @return репозиторій збереженого проєкту.
     */
    MapRepository createSavedProjectRepository(ProjectDescriptor project);
}
