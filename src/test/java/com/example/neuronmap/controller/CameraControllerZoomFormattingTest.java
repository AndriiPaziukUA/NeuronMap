package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.WorkspaceView;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.ScrollEvent;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє, що повідомлення про масштаб камери дотримується спільного формату чисел незалежно від системної локалі. */
final class CameraControllerZoomFormattingTest {

    /** Перевіряє десятковий роздільник відсотків у повідомленні про масштаб за німецької локалі форматування.
     *
     * @throws Exception якщо JavaFX-потік не завершить перевірку вчасно.
     */
    @Test
    void zoomStatusUsesRootLocaleForPercentageFormatting() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Locale previousLocale = Locale.getDefault(Locale.Category.FORMAT);
            CameraController controller = null;
            try {
                Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
                EditorState state = new EditorState(1.0, 0.0, 0.0);
                WorkspaceView workspace = new WorkspaceView();
                Scene scene = new Scene(workspace.node(), 800.0, 600.0);
                workspace.node().resize(800.0, 600.0);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                AtomicReference<String> status = new AtomicReference<>();
                controller = new CameraController(
                        state,
                        workspace,
                        () -> { },
                        () -> { },
                        () -> false,
                        ignored -> false,
                        status::set,
                        ignored -> { },
                        1.255,
                        new LocalizationService(Locale.ENGLISH)
                );
                controller.install();

                Event.fireEvent(workspace.node(), scrollEvent());

                assertEquals(1.255, state.zoom(), 0.000001);
                assertEquals("Zoom: 125.5%", status.get());
            } finally {
                if (controller != null) {
                    controller.dispose();
                }
                Locale.setDefault(Locale.Category.FORMAT, previousLocale);
            }
        });
    }

    /** Створює прокручування вгору, яке збільшує масштаб камери на один крок.
     *
     * @return JavaFX-подія прокручування для полотна редактора.
     */
    private static ScrollEvent scrollEvent() {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                100.0, 100.0, 100.0, 100.0,
                false, false, false, false,
                false, false,
                0.0, 1.0, 0.0, 1.0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0, null
        );
    }
}
