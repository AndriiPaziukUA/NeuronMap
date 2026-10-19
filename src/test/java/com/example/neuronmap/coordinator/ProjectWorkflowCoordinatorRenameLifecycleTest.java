package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.project.FileSystemProjectCatalogRepository;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.project.ProjectDescriptor;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.ProjectCatalogService;
import com.example.neuronmap.testsupport.TransientRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що перейменування активного проєкту закриває старий репозиторій рівно один раз,
 * зокрема коли координатор мусить відновити підключення після помилки.
 */
final class ProjectWorkflowCoordinatorRenameLifecycleTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Створює набір ізольованих тестів життєвого циклу репозиторію під час перейменування.
     */
    ProjectWorkflowCoordinatorRenameLifecycleTest() {
    }

    /**
     * Перевіряє одноразове закриття попереднього сховища після успішного перейменування активного проєкту.
     *
     * @throws IOException якщо не вдається створити тестовий проєкт.
     */
    @Test
    void closesDetachedRepositoryOnceAfterSuccessfulRename() throws IOException {
        ProjectCatalogService catalog = catalog(temporaryDirectory.resolve("projects"));
        ProjectDescriptor project = createSavedProject(catalog.storageDirectory(), "Source");
        TrackingRepository originalRepository = trackingRepository(project.databasePath());
        MapService mapService = new MapService(new NeuronMapModel(), originalRepository);
        TrackingRepositoryFactory repositoryFactory = new TrackingRepositoryFactory();
        AtomicReference<ProjectDescriptor> currentProject = new AtomicReference<>(project);
        ProjectWorkflowCoordinator coordinator = coordinator(
                catalog,
                mapService,
                repositoryFactory,
                currentProject,
                ignored -> { }
        );

        try {
            ProjectDescriptor renamed = coordinator.renameProject(project, "Renamed");
            TrackingRepository renamedRepository = repositoryFactory.savedRepositories().getFirst();

            assertEquals("Renamed", renamed.name());
            assertTrue(mapService.isActiveRepository(renamedRepository));
            assertEquals(1, originalRepository.closeCount());
            assertEquals(0, renamedRepository.closeCount());
        } finally {
            mapService.close();
        }

        assertEquals(1, originalRepository.closeCount());
        assertEquals(1, repositoryFactory.savedRepositories().getFirst().closeCount());
    }

    /**
     * Перевіряє, що відновлення підключення після помилки не закриває вдруге вже від'єднаний репозиторій.
     *
     * @throws IOException якщо не вдається створити тестовий проєкт.
     */
    @Test
    void doesNotCloseDetachedRepositoryAgainDuringFailedRenameRecovery() throws IOException {
        ProjectCatalogService catalog = catalog(temporaryDirectory.resolve("projects"));
        ProjectDescriptor project = createSavedProject(catalog.storageDirectory(), "Source");
        TrackingRepository originalRepository = trackingRepository(project.databasePath());
        MapService mapService = new MapService(new NeuronMapModel(), originalRepository);
        TrackingRepositoryFactory repositoryFactory = new TrackingRepositoryFactory();
        AtomicReference<ProjectDescriptor> currentProject = new AtomicReference<>(project);
        ProjectWorkflowCoordinator coordinator = coordinator(
                catalog,
                mapService,
                repositoryFactory,
                currentProject,
                currentProject::set
        );

        try {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> coordinator.renameProject(project, ".")
            );

            assertNotNull(thrown.getMessage());
            assertTrue(Files.isRegularFile(project.databasePath()));
            assertEquals(1, originalRepository.closeCount());
            assertEquals(1, repositoryFactory.savedRepositories().size());
            assertEquals(0, repositoryFactory.savedRepositories().getFirst().closeCount());
            assertTrue(mapService.isActiveRepository(repositoryFactory.savedRepositories().getFirst()));
        } finally {
            mapService.close();
        }

        assertEquals(1, originalRepository.closeCount());
        assertEquals(1, repositoryFactory.savedRepositories().getFirst().closeCount());
    }

    /**
     * Створює координатор із потрібними залежностями та переданим обробником оновлення поточного проєкту.
     *
     * @param catalog служба тестового каталогу проєктів.
     * @param mapService служба активної карти.
     * @param repositoryFactory фабрика відстежуваних репозиторіїв.
     * @param currentProject посилання на активний проєкт.
     * @param projectConsumer обробник оновлення активного проєкту.
     * @return координатор для тестування життєвого циклу під час перейменування.
     */
    private ProjectWorkflowCoordinator coordinator(
            ProjectCatalogService catalog,
            MapService mapService,
            TrackingRepositoryFactory repositoryFactory,
            AtomicReference<ProjectDescriptor> currentProject,
            java.util.function.Consumer<ProjectDescriptor> projectConsumer
    ) {
        return new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                repositoryFactory,
                currentProject::get,
                projectConsumer,
                () -> { },
                () -> { },
                (project, repository, loadExisting) -> { }
        );
    }

    /**
     * Створює на диску збережений проєкт і повертає його опис із каталогу.
     *
     * @param root кореневий каталог проєктів.
     * @param name назва тестового проєкту.
     * @return дескриптор створеного проєкту.
     * @throws IOException якщо не вдається створити каталог або файл бази даних.
     */
    private ProjectDescriptor createSavedProject(Path root, String name) throws IOException {
        Path databasePath = root.resolve(name).resolve("project.db");
        Files.createDirectories(databasePath.getParent());
        Files.writeString(databasePath, "test-project-data");
        return catalog(root).listProjects().stream()
                .filter(project -> project.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Створює службу каталогу з файловим адаптером та ізольованим сховищем загальних налаштувань.
     *
     * @param root кореневий каталог тестових проєктів.
     * @return службу каталогу проєктів.
     */
    private ProjectCatalogService catalog(Path root) {
        return new ProjectCatalogService(new FileSystemProjectCatalogRepository(
                root,
                new GlobalSettingsStore(temporaryDirectory.resolve("global.properties"))
        ));
    }

    /**
     * Створює репозиторій-обгортку, яка підраховує фактичні виклики закриття.
     *
     * @param databasePath шлях до бази даних репозиторію.
     * @return відстежуваний репозиторій для тесту.
     */
    private TrackingRepository trackingRepository(Path databasePath) {
        return new TrackingRepository(TransientRepositoryTestSupport.create(databasePath));
    }

    /**
     * Фабрика репозиторіїв, яка зберігає створені екземпляри для перевірки передавання володіння.
     */
    private static final class TrackingRepositoryFactory implements MapRepositoryFactory {

        private final List<TrackingRepository> savedRepositories = new ArrayList<>();

        /**
         * Створює фабрику з порожнім списком відстежуваних репозиторіїв.
         */
        private TrackingRepositoryFactory() {
        }

        /**
         * Створює репозиторій для тимчасового проєкту.
         *
         * @param project опис тимчасового проєкту.
         * @return відстежуваний репозиторій.
         */
        @Override
        public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
            return new TrackingRepository(TransientRepositoryTestSupport.create(project.databasePath()));
        }

        /**
         * Створює та реєструє репозиторій для збереженого проєкту.
         *
         * @param project опис збереженого проєкту.
         * @return відстежуваний репозиторій.
         */
        @Override
        public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
            TrackingRepository repository = new TrackingRepository(
                    TransientRepositoryTestSupport.create(project.databasePath())
            );
            savedRepositories.add(repository);
            return repository;
        }

        /**
         * Повертає створені репозиторії в порядку їх передавання службі карти.
         *
         * @return список зареєстрованих репозиторіїв.
         */
        private List<TrackingRepository> savedRepositories() {
            return List.copyOf(savedRepositories);
        }
    }

    /**
     * Делегує операції репозиторію та рахує кількість викликів його закриття.
     */
    private static final class TrackingRepository implements MapRepository {

        private final MapRepository delegate;
        private int closeCount;

        /**
         * Створює обгортку для переданого репозиторію.
         *
         * @param delegate репозиторій, виклики якого потрібно відстежувати.
         */
        private TrackingRepository(MapRepository delegate) {
            this.delegate = delegate;
        }

        /**
         * Повертає шлях до бази даних делегованого репозиторію.
         *
         * @return шлях до бази даних.
         */
        @Override
        public Path databasePath() {
            return delegate.databasePath();
        }

        /**
         * Повертає ознаку постійного збереження даних делегованим репозиторієм.
         *
         * @return {@code true}, якщо репозиторій зберігає дані постійно.
         */
        @Override
        public boolean isPersistent() {
            return delegate.isPersistent();
        }

        /**
         * Завантажує стан камери через делегований репозиторій.
         *
         * @return стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            return delegate.loadCameraState();
        }

        /**
         * Завантажує тривалість такту симуляції через делегований репозиторій.
         *
         * @param fallbackMillis резервна тривалість такту.
         * @return збережена або резервна тривалість такту.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return delegate.loadSimulationTickMillis(fallbackMillis);
        }

        /**
         * Завантажує дані карти через делегований репозиторій.
         *
         * @param model модель, яку потрібно наповнити даними.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
            delegate.loadInto(model);
        }

        /**
         * Зберігає тривалість такту симуляції через делегований репозиторій.
         *
         * @param millis тривалість такту в мілісекундах.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
            delegate.saveSimulationTickMillis(millis);
        }

        /**
         * Зберігає модель карти та стан камери через делегований репозиторій.
         *
         * @param model модель карти.
         * @param cameraState стан камери.
         */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            delegate.save(model, cameraState);
        }

        /**
         * Рахує виклик закриття та звільняє делегований репозиторій.
         */
        @Override
        public void close() {
            closeCount++;
            delegate.close();
        }

        /**
         * Повертає кількість викликів закриття обгортки.
         *
         * @return кількість викликів {@link #close()}.
         */
        private int closeCount() {
            return closeCount;
        }
    }
}
