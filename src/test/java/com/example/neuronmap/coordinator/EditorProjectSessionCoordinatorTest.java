package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє, що підготовка до перемикання проєкту не виконує зайвого безумовного збереження. */
final class EditorProjectSessionCoordinatorTest {

    /** Перевіряє, що звичайне перемикання не записує незмінений проєкт повторно. */
    @Test
    void doesNotSaveProjectUnconditionallyBeforeSwitch() {
        NeuronMapModel model = new NeuronMapModel();
        MapService mapService = new MapService(model, new NoOpMapRepository());
        HistoryService historyService = new HistoryService(model);
        EditorState state = new EditorState(CameraState.defaultState());
        AtomicInteger saveCalls = new AtomicInteger();

        EditorProjectSessionCoordinator coordinator = new EditorProjectSessionCoordinator(
                mapService,
                historyService,
                state,
                ignored -> { },
                ignored -> { },
                ignored -> { },
                100.0,
                5000.0,
                650.0,
                new EditorProjectSessionCoordinator.SessionActions() {
                    @Override
                    public void stopSimulation() { }

                    @Override
                    public void clearConnectionHighlights() { }

                    @Override
                    public void cancelInteractions() { }

                    @Override
                    public void hideNeuronMenu() { }

                    @Override
                    public void cancelPendingCameraSave() { }

                    @Override
                    public void saveCurrentProject() {
                        saveCalls.incrementAndGet();
                    }

                    @Override
                    public boolean pauseSimulationForModal() {
                        return false;
                    }

                    @Override
                    public void resumeSimulationFromModal() { }

                    @Override
                    public void pauseConnectionAnimations() { }

                    @Override
                    public void resumeConnectionAnimations() { }

                    @Override
                    public void updateSimulationSpeed(double millis) { }

                    @Override
                    public void synchronizeViewsWithModel() { }

                    @Override
                    public void applyCamera() { }

                    @Override
                    public void showMainPage() { }
                }
        );

        coordinator.prepareForProjectSwitch();

        assertEquals(0, saveCalls.get());
    }

    private static final class NoOpMapRepository implements MapRepository {

        @Override
        public Path databasePath() {
            return Path.of("unused-test-project.db");
        }

        @Override
        public CameraState loadCameraState() {
            return CameraState.defaultState();
        }

        @Override
        public double loadSimulationTickMillis(double fallbackMillis) {
            return fallbackMillis;
        }

        @Override
        public void loadInto(NeuronMapModel model) { }

        @Override
        public void saveSimulationTickMillis(double millis) { }

        @Override
        public void save(NeuronMapModel model, CameraState cameraState) { }

        @Override
        public void close() { }
    }
}
