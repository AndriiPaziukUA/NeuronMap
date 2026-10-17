package com.example.neuronmap.coordinator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вкладене призупинення UI-процесів та безпечне керування їхніми реєстраціями. */
final class UiProcessCoordinatorTest {

    /** Перевіряє, що процеси призупиняються один раз і відновлюються у зворотному порядку. */
    @Test
    void nestedSuspensionsKeepProcessesPausedUntilTheLastReasonIsRemoved() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        coordinator.register("status", () -> events.add("pause-status"), () -> events.add("resume-status"));
        coordinator.register("simulation", () -> events.add("pause-simulation"), () -> events.add("resume-simulation"));

        coordinator.suspend("main-menu");
        coordinator.suspend("project-dialog");
        coordinator.resume("main-menu");

        assertTrue(coordinator.isSuspended());
        assertEquals(List.of("pause-status", "pause-simulation"), events);

        coordinator.resume("project-dialog");

        assertFalse(coordinator.isSuspended());
        assertEquals(List.of(
                "pause-status", "pause-simulation", "resume-simulation", "resume-status"
        ), events);
    }

    /** Перевіряє ідемпотентність повторного встановлення та зняття тієї самої причини паузи. */
    @Test
    void duplicateSuspensionReasonDoesNotRepeatCallbacks() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        coordinator.register("status", () -> events.add("pause"), () -> events.add("resume"));

        coordinator.suspend("main-menu");
        coordinator.suspend("main-menu");
        coordinator.resume("main-menu");
        coordinator.resume("main-menu");

        assertEquals(List.of("pause", "resume"), events);
    }

    /** Перевіряє, що процес, зареєстрований під час паузи, призупиняється негайно. */
    @Test
    void processRegisteredDuringSuspensionIsImmediatelyPaused() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        coordinator.suspend("main-menu");

        coordinator.register("status", () -> events.add("pause"), () -> events.add("resume"));
        coordinator.resume("main-menu");

        assertEquals(List.of("pause", "resume"), events);
    }

    /** Перевіряє, що закриття призупиненої реєстрації відновлює її перед від'єднанням. */
    @Test
    void closingRegistrationDuringSuspensionResumesOnlyThatProcess() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        UiProcessCoordinator.Registration registration = coordinator.register(
                "status", () -> events.add("pause"), () -> events.add("resume")
        );
        coordinator.suspend("main-menu");

        registration.close();
        registration.close();
        coordinator.resume("main-menu");

        assertEquals(List.of("pause", "resume"), events);
    }

    /** Перевіряє відхилення некоректних ідентифікаторів та повторної реєстрації процесу. */
    @Test
    void rejectsBlankIdentifiersAndDuplicateProcessNames() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        coordinator.register("status", () -> { }, () -> { });

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.register("  ", () -> { }, () -> { }));
        assertThrows(IllegalStateException.class,
                () -> coordinator.register("status", () -> { }, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> coordinator.suspend(" "));
    }


    /** Перевіряє відкат уже призупинених процесів, якщо пауза одного з наступних процесів завершується помилкою. */
    @Test
    void failedSuspensionRollsBackPreviouslyPausedProcesses() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        coordinator.register("first", () -> events.add("pause-first"), () -> events.add("resume-first"));
        coordinator.register("failing", () -> {
            events.add("pause-failing");
            throw new IllegalStateException("pause failed");
        }, () -> events.add("resume-failing"));

        assertThrows(IllegalStateException.class, () -> coordinator.suspend("main-menu"));

        assertFalse(coordinator.isSuspended());
        assertEquals(List.of("pause-first", "pause-failing", "resume-first"), events);
    }

    /** Перевіряє, що невдале відновлення повторно призупиняє процеси й зберігає причину паузи. */
    @Test
    void failedResumeRestoresSuspendedState() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        List<String> events = new ArrayList<>();
        coordinator.register("first", () -> events.add("pause-first"), () -> {
            events.add("resume-first-failing");
            throw new IllegalStateException("resume failed");
        });
        coordinator.register("second", () -> events.add("pause-second"), () -> events.add("resume-second"));
        coordinator.suspend("main-menu");

        assertThrows(IllegalStateException.class, () -> coordinator.resume("main-menu"));

        assertTrue(coordinator.isSuspended());
        assertEquals(List.of(
                "pause-first", "pause-second", "resume-second", "resume-first-failing", "pause-second"
        ), events);
        coordinator.dispose();
    }

    /** Перевіряє, що завершений координатор від'єднано й більше не приймає нові процеси. */
    @Test
    void disposedCoordinatorRejectsNewRegistrationsAndSuspensions() {
        UiProcessCoordinator coordinator = new UiProcessCoordinator();
        coordinator.dispose();

        assertThrows(IllegalStateException.class,
                () -> coordinator.register("status", () -> { }, () -> { }));
        assertThrows(IllegalStateException.class, () -> coordinator.suspend("main-menu"));
    }
}
