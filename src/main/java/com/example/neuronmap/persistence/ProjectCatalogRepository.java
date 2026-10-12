package com.example.neuronmap.persistence;

import com.example.neuronmap.project.ProjectDescriptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Визначає контракт збереження та отримання даних каталогу проєктів без залежності від прикладного шару.
 */
public interface ProjectCatalogRepository {

    /**
     * Повертає кореневий каталог збереження проєктів.
     *
     * @return нормалізований абсолютний шлях до каталогу збереження.
     */
    Path storageDirectory();

    /**
     * Повертає описи всіх проєктів, які мають збережений файл бази даних.
     *
     * @return список збережених проєктів.
     */
    List<ProjectDescriptor> listSavedProjects();

    /**
     * Створює опис проєкту для заданого шляху до бази даних.
     *
     * @param databasePath шлях до файлу бази даних проєкту.
     * @return опис проєкту, що відповідає заданому шляху.
     */
    ProjectDescriptor descriptorForDatabasePath(Path databasePath);

    /**
     * Перейменовує каталог збереженого проєкту та повертає його оновлений опис.
     *
     * @param project опис проєкту, який потрібно перейменувати.
     * @param newName нова назва каталогу проєкту.
     * @return оновлений опис перейменованого проєкту.
     */
    ProjectDescriptor renameProject(ProjectDescriptor project, String newName);

    /**
     * Видаляє файли та каталоги, що належать заданому проєкту.
     *
     * @param project опис проєкту, який потрібно видалити.
     */
    void deleteProjectFiles(ProjectDescriptor project);

    /**
     * Повертає назву останнього відкритого проєкту, якщо її збережено.
     *
     * @return назва останнього відкритого проєкту або {@code null}, якщо її немає.
     */
    String loadLastOpenedProjectName();

    /**
     * Зберігає назву останнього відкритого проєкту.
     *
     * @param projectName назва проєкту або {@code null}, щоб очистити значення.
     */
    void saveLastOpenedProjectName(String projectName);
}
