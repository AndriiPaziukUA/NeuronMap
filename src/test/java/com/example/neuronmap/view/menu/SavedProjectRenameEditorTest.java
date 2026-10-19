package com.example.neuronmap.view.menu;

import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє життєвий цикл поля inline-редагування назви проєкту. */
final class SavedProjectRenameEditorTest {

    /** Перевіряє введення назви, передавання Enter власнику рядка та звільнення посилання на поле. */
    @Test
    void enterDelegatesConfirmationAndFinishReleasesEditor() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            SavedProjectRenameEditor editor = new SavedProjectRenameEditor();
            AtomicInteger confirmations = new AtomicInteger();
            TextField field = editor.begin("Початкова назва", confirmations::incrementAndGet);

            assertTrue(editor.isEditing());
            assertEquals("Початкова назва", editor.text());
            assertTrue(field.getStyleClass().contains("saved-project-rename-field"));

            field.setText("Нова назва");
            Event.fireEvent(field, new ActionEvent());

            assertEquals("Нова назва", editor.text());
            assertEquals(1, confirmations.get());

            editor.finish();
            assertFalse(editor.isEditing());
            assertNull(editor.field());
            assertEquals("", editor.text());
            editor.finish();
        });
    }

    /** Перевіряє, що один рядок не може паралельно створити два активні поля перейменування. */
    @Test
    void rejectsStartingAnotherEditBeforeFinishingCurrentOne() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            SavedProjectRenameEditor editor = new SavedProjectRenameEditor();
            editor.begin("Назва", () -> { });

            assertThrows(IllegalStateException.class, () -> editor.begin("Інша назва", () -> { }));

            editor.finish();
        });
    }
}
