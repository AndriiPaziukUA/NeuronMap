package com.example.neuronmap.coordinator;

import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Координує сценарії створення, відкриття, перейменування та видалення проєктів редактора.
 */
public final class ProjectWorkflowCoordinator {

    private final ProjectCatalogService projectCatalog;
    private final LocalizationService localization;
    private final MapService mapService;
    private final MapRepositoryFactory mapRepositoryFactory;
    private final Supplier<ProjectDescriptor> currentProjectSupplier;
    private final Consumer<ProjectDescriptor> currentProjectConsumer;
    private final Runnable prepareForProjectSwitch;
    private final Runnable saveNow;
    private final ProjectActivator projectActivator;

    /**
     * Створює координатор робочих сценаріїв проєктів із залежностями, потрібними для переходу між проєктами.
     *
     * @param projectCatalog служба каталогу проєктів.
     * @param localization служба локалізації інтерфейсу.
     * @param mapService служба активної карти та її сховища.
     * @param mapRepositoryFactory фабрика репозиторіїв тимчасових і збережених проєктів.
     * @param currentProjectSupplier постачальник опису поточного проєкту.
     * @param currentProjectConsumer отримувач оновленого опису поточного проєкту.
     * @param prepareForProjectSwitch підготовка редактора до зміни проєкту.
     * @param saveNow дія негайного збереження поточного стану.
     * @param projectActivator дія підключення та активації проєкту.
     */
    public ProjectWorkflowCoordinator(
            ProjectCatalogService projectCatalog,
            LocalizationService localization,
            MapService mapService,
            MapRepositoryFactory mapRepositoryFactory,
            Supplier<ProjectDescriptor> currentProjectSupplier,
            Consumer<ProjectDescriptor> currentProjectConsumer,
            Runnable prepareForProjectSwitch,
            Runnable saveNow,
            ProjectActivator projectActivator
    ) {
        this.projectCatalog = Objects.requireNonNull(projectCatalog, "projectCatalog");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.mapRepositoryFactory = Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory");
        this.currentProjectSupplier = Objects.requireNonNull(currentProjectSupplier, "currentProjectSupplier");
        this.currentProjectConsumer = Objects.requireNonNull(currentProjectConsumer, "currentProjectConsumer");
        this.prepareForProjectSwitch = Objects.requireNonNull(prepareForProjectSwitch, "prepareForProjectSwitch");
        this.saveNow = Objects.requireNonNull(saveNow, "saveNow");
        this.projectActivator = Objects.requireNonNull(projectActivator, "projectActivator");
    }

    /**
     * Створює тимчасовий проєкт із типовою назвою та передає його редактору для активації.
     */
    public void createNewProject() {
        prepareForProjectSwitch.run();
        ProjectDescriptor project = projectCatalog.createTransientProject(
                localization.text("project.default_name")
        );
        MapRepository repository = mapRepositoryFactory.createTransientProjectRepository(project);
        activateProjectWithCleanup(project, repository, false);
    }

    /**
     * Відкриває вибраний збережений проєкт, якщо він не є поточним.
     *
     * @param project опис проєкту, який потрібно відкрити.
     */
    public void openProject(ProjectDescriptor project) {
        if (project == null) {
            return;
        }
        ProjectDescriptor currentProject = currentProjectSupplier.get();
        if (currentProject != null && currentProject.id().equals(project.id())) {
            return;
        }

        prepareForProjectSwitch.run();
        MapRepository repository = mapRepositoryFactory.createSavedProjectRepository(project);
        activateProjectWithCleanup(project, repository, true);
    }

    /**
     * Від'єднує активний репозиторій перед файловим перейменуванням і відновлює підключення, якщо операція не вдалася.
     * Життєвим циклом репозиторію керує {@link MapService}; якщо повторне підключення також не вдається,
     * ця помилка додається до первісної як suppressed,
     * щоб не приховувати початкову причину невдалого перейменування.
     *
     * @param project опис проєкту, який потрібно перейменувати.
     * @param name нова назва проєкту.
     * @return оновлений опис проєкту або початковий опис, якщо аргументи непридатні.
     * @throws RuntimeException якщо від'єднання, перейменування або підключення репозиторію не вдалося.
     */
    public ProjectDescriptor renameProject(
            ProjectDescriptor project,
            String name
    ) {
        if (project == null || name == null || name.isBlank()) {
            return project;
        }

        ProjectDescriptor currentProject = currentProjectSupplier.get();
        boolean active = currentProject != null
                && currentProject.databasePath().equals(project.databasePath());
        if (!active) {
            return projectCatalog.rename(project, name);
        }

        saveNow.run();

        try {
            mapService.detachRepository();
            ProjectDescriptor renamed = projectCatalog.rename(project, name);
            mapService.switchRepository(
                    mapRepositoryFactory.createSavedProjectRepository(renamed)
            );
            currentProjectConsumer.accept(renamed);
            projectCatalog.markLastOpened(Optional.of(renamed));
            return renamed;
        } catch (RuntimeException exception) {
            try {
                mapService.switchRepository(
                        mapRepositoryFactory.createSavedProjectRepository(project)
                );
                currentProjectConsumer.accept(projectCatalog.descriptorForDatabasePath(
                        project.databasePath()
                ));
            } catch (RuntimeException recoveryException) {
                if (recoveryException != exception) {
                    exception.addSuppressed(recoveryException);
                }
            }
            throw exception;
        }
    }

    /**
     * Видаляє вибраний проєкт і, якщо його було відкрито, активує тимчасовий або інший доступний проєкт.
     *
     * @param project опис проєкту, який потрібно видалити.
     */
    public void deleteProject(ProjectDescriptor project) {
        if (project == null) {
            return;
        }

        ProjectDescriptor currentProject = currentProjectSupplier.get();
        boolean active = currentProject != null
                && currentProject.id().equals(project.id());

        if (!active) {
            projectCatalog.delete(project);
            return;
        }

        prepareForProjectSwitch.run();
        ProjectDescriptor replacement = projectCatalog.createTransientProject(
                localization.text("project.default_name")
        );
        MapRepository repository = mapRepositoryFactory.createTransientProjectRepository(replacement);
        activateProjectWithCleanup(replacement, repository, false);
        projectCatalog.delete(project);

        List<ProjectDescriptor> remaining = projectCatalog.listProjects();
        if (!remaining.isEmpty()) {
            openProject(remaining.getFirst());
        }
    }

    /**
     * Виконує активацію проєкту та звільняє репозиторій, якщо активація не передала його службі карти.
     * Помилка очищення додається до винятку активації, щоб не приховувати першопричину.
     *
     * @param project опис проєкту, який потрібно активувати.
     * @param repository репозиторій, що передається активатору.
     * @param loadExisting ознака потреби завантажити вміст наявного проєкту.
     */
    private void activateProjectWithCleanup(
            ProjectDescriptor project,
            MapRepository repository,
            boolean loadExisting
    ) {
        try {
            projectActivator.activate(project, repository, loadExisting);
        } catch (RuntimeException | Error activationFailure) {
            // Після передачі репозиторію службі карти відповідальність за нього вже перейшла до MapService.
            if (mapService.isActiveRepository(repository)) {
                throw activationFailure;
            }

            try {
                repository.close();
            } catch (RuntimeException | Error closeFailure) {
                if (closeFailure != activationFailure) {
                    activationFailure.addSuppressed(closeFailure);
                }
            }
            throw activationFailure;
        }
    }

    /**
     * Описує дію, що підключає сховище вибраного проєкту та відновлює його стан у редакторі.
     */
    @FunctionalInterface
    public interface ProjectActivator {

        /**
         * Активує проєкт і за потреби завантажує його збережений вміст.
         *
         * @param project опис проєкту, який потрібно активувати.
         * @param repository репозиторій даних вибраного проєкту.
         * @param loadExisting ознака потреби завантажити наявний вміст проєкту.
         */
        void activate(ProjectDescriptor project, MapRepository repository, boolean loadExisting);
    }
}
