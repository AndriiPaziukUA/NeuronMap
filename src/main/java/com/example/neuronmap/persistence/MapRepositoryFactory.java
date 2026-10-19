package com.example.neuronmap.persistence;

import com.example.neuronmap.project.ProjectDescriptor;

/**
 * Визначає створення репозиторіїв для тимчасових і збережених проєктів та їхніх постійних делегатів.
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

    /**
     * Створює постійний репозиторій, який стає делегатом після матеріалізації тимчасового проєкту.
     * Типова реалізація повторно використовує механізм відкриття збереженого проєкту, а спеціалізовані фабрики
     * можуть перевизначити цей метод, якщо створення делегата має відрізнятися.
     *
     * @param project опис проєкту, який матеріалізується.
     * @return постійний репозиторій для подальших операцій збереження.
     */
    default MapRepository createPersistentDelegate(ProjectDescriptor project) {
        return createSavedProjectRepository(project);
    }
}
