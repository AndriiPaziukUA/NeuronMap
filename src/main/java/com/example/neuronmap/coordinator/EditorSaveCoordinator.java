package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;

import java.util.Objects;
import java.util.Optional;

/**
 * Координує збереження карти, стану камери, метаданих активного проєкту та історії змін редактора.
 */
public final class EditorSaveCoordinator {

    private final MapService mapService;
    private final ProjectCatalogService projectCatalog;
    private final HistoryService historyService;

    /**
     * Створює координатор збереження з готовими службами карти, каталогу та історії.
     *
     * @param mapService служба збереження карти й стану камери.
     * @param projectCatalog служба збереження метаданих активного проєкту.
     * @param historyService служба фіксації стану в історії змін.
     */
    public EditorSaveCoordinator(
            MapService mapService,
            ProjectCatalogService projectCatalog,
            HistoryService historyService
    ) {
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.projectCatalog = Objects.requireNonNull(projectCatalog, "projectCatalog");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
    }

    /**
     * Зберігає карту й камеру, оновлює метадані постійного проєкту та фіксує історію, якщо її ініціалізовано.
     *
     * @param state поточний стан редактора, з якого беруться параметри камери.
     * @param currentProject опис активного проєкту до збереження.
     * @param historyInitialized ознака, що історію вже ініціалізовано.
     * @return оновлений опис активного проєкту або початковий опис для тимчасового сховища.
     */
    public ProjectDescriptor save(
            EditorState state,
            ProjectDescriptor currentProject,
            boolean historyInitialized
    ) {
        Objects.requireNonNull(state, "state");

        CameraState cameraState = new CameraState(
                state.zoom(),
                state.panX(),
                state.panY()
        );
        mapService.save(cameraState);

        if (mapService.isPersistent()) {
            currentProject = projectCatalog.descriptorForDatabasePath(
                    mapService.databasePath()
            );
            projectCatalog.markLastOpened(Optional.of(currentProject));
        }

        if (historyInitialized) {
            historyService.commitSavedState();
        }

        return currentProject;
    }
}
