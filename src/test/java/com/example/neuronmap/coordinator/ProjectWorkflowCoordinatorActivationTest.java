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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє передавання володіння репозиторіями та їх очищення у разі невдалої активації проєкту.
 */
final class ProjectWorkflowCoordinatorActivationTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Перевіряє закриття непереданого репозиторію та збереження первинної помилки, коли закриття теж завершується помилкою.
     */
    @Test
    void closesUnclaimedRepositoryAndSuppressesCloseFailure() {
        RuntimeException activationFailure = new IllegalStateException("Activation failed");
        RuntimeException closeFailure = new IllegalStateException("Close failed");
        AtomicReference<TrackingRepository> createdRepository = new AtomicReference<>();
        MapRepositoryFactory repositoryFactory = repositoryFactory(createdRepository, closeFailure);
        MapService mapService = mapService(temporaryDirectory.resolve("active/project.db"));
        ProjectWorkflowCoordinator coordinator = coordinator(
                mapService,
                repositoryFactory,
                new AtomicReference<>(),
                (project, repository, loadExisting) -> {
                    throw activationFailure;
                }
        );

        try {
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    coordinator::createNewProject
            );

            assertSame(activationFailure, thrown);
            assertEquals(1, createdRepository.get().closeCount());
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(closeFailure, thrown.getSuppressed()[0]);
        } finally {
            mapService.close();
        }
    }

    /**
     * Переконується, що репозиторій із тим самим шляхом, але іншим екземпляром, закривається після невдалого відкриття.
     */
    @Test
    void closesDistinctRepositoryEvenWhenDatabasePathMatchesActiveProject() {
        Path sharedPath = temporaryDirectory.resolve("shared/project.db");
        MapRepository activeRepository = TransientRepositoryTestSupport.create(sharedPath);
        MapService mapService = new MapService(new NeuronMapModel(), activeRepository);
        AtomicReference<TrackingRepository> createdRepository = new AtomicReference<>();
        ProjectDescriptor current = new ProjectDescriptor("current", "Current", sharedPath, null);
        ProjectDescriptor selected = new ProjectDescriptor("selected", "Selected", sharedPath, null);
        RuntimeException activationFailure = new IllegalStateException("Loading failed");
        ProjectWorkflowCoordinator coordinator = coordinator(
                mapService,
                repositoryFactory(createdRepository, null),
                new AtomicReference<>(current),
                (project, repository, loadExisting) -> {
                    throw activationFailure;
                }
        );

        try {
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> coordinator.openProject(selected)
            );

            assertSame(activationFailure, thrown);
            assertEquals(1, createdRepository.get().closeCount());
            assertTrue(mapService.isActiveRepository(activeRepository));
            assertFalse(mapService.isActiveRepository(createdRepository.get()));
        } finally {
            mapService.close();
        }
    }

    /**
     * Перевіряє, що невдала активація резервного проєкту не видаляє проєкт, який користувач намагався видалити.
     *
     * @throws IOException якщо не вдається створити тестовий файл проєкту.
     */
    @Test
    void doesNotDeleteActiveProjectWhenReplacementActivationFails() throws IOException {
        Path projectsRoot = temporaryDirectory.resolve("projects");
        Path database = projectsRoot.resolve("SavedProject/project.db");
        Files.createDirectories(database.getParent());
        Files.createFile(database);
        ProjectCatalogService catalog = catalog(projectsRoot);
        ProjectDescriptor projectToDelete = catalog.listProjects().getFirst();
        AtomicReference<TrackingRepository> createdRepository = new AtomicReference<>();
        MapRepositoryFactory repositoryFactory = repositoryFactory(createdRepository, null);
        MapService mapService = mapService(temporaryDirectory.resolve("active/project.db"));
        RuntimeException activationFailure = new IllegalStateException("Replacement activation failed");
        ProjectWorkflowCoordinator coordinator = new ProjectWorkflowCoordinator(
                catalog,
                new LocalizationService(Locale.ENGLISH),
                mapService,
                repositoryFactory,
                () -> projectToDelete,
                ignored -> { },
                () -> { },
                () -> { },
                (project, repository, loadExisting) -> {
                    throw activationFailure;
                }
        );

        try {
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> coordinator.deleteProject(projectToDelete)
            );

            assertSame(activationFailure, thrown);
            assertTrue(Files.isRegularFile(database));
            assertEquals(1, createdRepository.get().closeCount());
        } finally {
            mapService.close();
        }
    }

    /**
     * Перевіряє, що координатор не закриває репозиторій, який активатор уже передав службі карти.
     */
    @Test
    void doesNotCloseRepositoryAfterOwnershipHasTransferredToMapService() {
        AtomicReference<TrackingRepository> createdRepository = new AtomicReference<>();
        MapService mapService = mapService(temporaryDirectory.resolve("active/project.db"));
        RuntimeException activationFailure = new IllegalStateException("Post-switch activation failed");
        ProjectWorkflowCoordinator coordinator = coordinator(
                mapService,
                repositoryFactory(createdRepository, null),
                new AtomicReference<>(),
                (project, repository, loadExisting) -> {
                    mapService.switchRepository(repository);
                    throw activationFailure;
                }
        );

        try {
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    coordinator::createNewProject
            );

            assertSame(activationFailure, thrown);
            assertTrue(mapService.isActiveRepository(createdRepository.get()));
            assertEquals(0, createdRepository.get().closeCount());
        } finally {
            mapService.close();
        }

        assertEquals(1, createdRepository.get().closeCount());
    }

    /**
     * Створює координатор зі спільною тестовою інфраструктурою та переданим сценарієм активації.
     *
     * @param mapService служба карти, що зберігає поточне сховище.
     * @param repositoryFactory фабрика, яка надає тестові репозиторії.
     * @param currentProject атомарне посилання на поточний проєкт.
     * @param activator тестова дія активації.
     * @return координатор для перевірки поведінки активації.
     */
    private ProjectWorkflowCoordinator coordinator(
            MapService mapService,
            MapRepositoryFactory repositoryFactory,
            AtomicReference<ProjectDescriptor> currentProject,
            ProjectWorkflowCoordinator.ProjectActivator activator
    ) {
        return new ProjectWorkflowCoordinator(
                catalog(temporaryDirectory.resolve("catalog")),
                new LocalizationService(Locale.ENGLISH),
                mapService,
                repositoryFactory,
                currentProject::get,
                currentProject::set,
                () -> { },
                () -> { },
                activator
        );
    }

    /**
     * Створює фабрику, яка реєструє створений репозиторій і за потреби імітує помилку його закриття.
     *
     * @param createdRepository посилання для перевірки репозиторію, створеного фабрикою.
     * @param closeFailure виняток, який потрібно викинути під час закриття, або {@code null}.
     * @return фабрика репозиторіїв для ізольованих тестів.
     */
    private MapRepositoryFactory repositoryFactory(
            AtomicReference<TrackingRepository> createdRepository,
            RuntimeException closeFailure
    ) {
        return new MapRepositoryFactory() {
            /**
             * Створює відстежуваний репозиторій тимчасового проєкту.
             *
             * @param project опис проєкту.
             * @return відстежуваний репозиторій.
             */
            @Override
            public MapRepository createTransientProjectRepository(ProjectDescriptor project) {
                TrackingRepository repository = new TrackingRepository(project.databasePath(), closeFailure);
                createdRepository.set(repository);
                return repository;
            }

            /**
             * Створює відстежуваний репозиторій збереженого проєкту.
             *
             * @param project опис проєкту.
             * @return відстежуваний репозиторій.
             */
            @Override
            public MapRepository createSavedProjectRepository(ProjectDescriptor project) {
                TrackingRepository repository = new TrackingRepository(project.databasePath(), closeFailure);
                createdRepository.set(repository);
                return repository;
            }
        };
    }

    /**
     * Створює службу каталогу на основі файлового адаптера в тестовому каталозі.
     *
     * @param root кореневий каталог проєктів.
     * @return службу керування каталогом.
     */
    private ProjectCatalogService catalog(Path root) {
        return new ProjectCatalogService(new FileSystemProjectCatalogRepository(
                root,
                new GlobalSettingsStore(root.resolve("neuronmap-global.properties"))
        ));
    }

    /**
     * Створює службу карти з тимчасовим репозиторієм, який отримано через стандартну фабрику.
     *
     * @param databasePath тестовий шлях бази даних.
     * @return службу карти.
     */
    private static MapService mapService(Path databasePath) {
        return new MapService(new NeuronMapModel(), TransientRepositoryTestSupport.create(databasePath));
    }

    /**
     * Тестовий репозиторій, який рахує виклики закриття й за потреби викидає заданий виняток.
     */
    private static final class TrackingRepository implements MapRepository {

        private final Path databasePath;
        private final RuntimeException closeFailure;
        private final AtomicInteger closeCount = new AtomicInteger();

        /**
         * Створює репозиторій, прив'язаний до тестового шляху.
         *
         * @param databasePath шлях до бази даних.
         * @param closeFailure необов'язковий виняток, який імітує помилку закриття.
         */
        private TrackingRepository(Path databasePath, RuntimeException closeFailure) {
            this.databasePath = databasePath;
            this.closeFailure = closeFailure;
        }

        /**
         * Повертає шлях тестового репозиторію.
         *
         * @return тестовий шлях до бази даних.
         */
        @Override
        public Path databasePath() {
            return databasePath;
        }

        /**
         * Повертає типовий стан камери без читання диска.
         *
         * @return типовий стан камери.
         */
        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        /**
         * Повертає резервну тривалість такту симуляції.
         *
         * @param fallbackMillis резервне значення.
         * @return передане резервне значення.
         */
        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        /**
         * Залишає модель без змін, оскільки тест не завантажує графічні дані.
         *
         * @param model модель карти.
         */
        @Override
        public void loadInto(NeuronMapModel model) {
            // Сховище використовується лише для перевірки передавання володіння ресурсом.
        }

        /**
         * Не виконує запис налаштувань симуляції в тесті.
         *
         * @param millis тривалість такту симуляції.
         */
        @Override
        public void saveSimulationTickMillis(double millis) {
            // Запис не потрібен для перевірки життєвого циклу.
        }

        /**
         * Не виконує запис карти в тесті.
         *
         * @param model модель карти.
         * @param cameraState стан камери.
         */
        @Override
        public void save(NeuronMapModel model, CameraState cameraState) {
            // Запис не потрібен для перевірки життєвого циклу.
        }

        /**
         * Рахує закриття й за потреби відтворює помилку звільнення ресурсу.
         */
        @Override
        public void close() {
            closeCount.incrementAndGet();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        /**
         * Повертає кількість спроб закриття репозиторію.
         *
         * @return кількість викликів {@link #close()}.
         */
        private int closeCount() {
            return closeCount.get();
        }
    }
}
