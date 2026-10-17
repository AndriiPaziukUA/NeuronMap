package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import com.example.neuronmap.project.ProjectDescriptor;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє іконки, локалізовані підказки та візуальні стани дій рядка збереженого проєкту. */
final class SavedProjectRowViewTest {

    /** Запускає JavaFX toolkit перед створенням кнопок і подань у тестах. */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Переконується, що звичайні дії рядка не мають тексту й отримують підказки активною мовою. */
    @Test
    void iconButtonsUseLocalizedActionNamesAsTooltips() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            SavedProjectRowView row = createRow(localization, new AtomicBoolean());
            try {
                Button rename = row.renameButtonForTest();
                Button delete = row.deleteButtonForTest();

                assertEquals("", rename.getText());
                assertTrue(rename.getGraphic() instanceof SVGPath,
                        "Перейменування має відображатися контурною іконкою олівця");
                assertEquals("Перейменувати", tooltipText(rename));
                assertEquals("Перейменувати", rename.getAccessibleText());

                assertEquals("", delete.getText());
                assertTrue(delete.getGraphic() instanceof Region,
                        "Видалення має відображатися графічним дефісом, а не текстовим символом");
                assertTrue(delete.getGraphic().getStyleClass().contains("saved-project-delete-icon"));
                assertEquals("Видалити", tooltipText(delete));
                assertEquals("Видалити", delete.getAccessibleText());

                localization.previewLanguage(SupportedLanguage.ENGLISH);
                assertEquals("Rename", tooltipText(rename));
                assertEquals("Delete", tooltipText(delete));
                localization.previewLanguage(SupportedLanguage.RUSSIAN);
                assertEquals("Переименовать", tooltipText(rename));
                assertEquals("Удалить", tooltipText(delete));
                localization.previewLanguage(SupportedLanguage.UKRAINIAN);
                assertEquals("Перейменувати", tooltipText(rename));
                assertEquals("Видалити", tooltipText(delete));
            } finally {
                row.dispose();
            }
        });
    }

    /** Перевіряє, що збереження й скасування редагування не показують спливаючих підказок. */
    @Test
    void saveAndCancelButtonsHaveNoTooltipsDuringRename() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            SavedProjectRowView row = createRow(localization, new AtomicBoolean());
            try {
                row.renameButtonForTest().fire();

                assertTrue(row.isEditing());
                assertEquals("Зберегти", row.renameButtonForTest().getText());
                assertNull(row.renameButtonForTest().getGraphic());
                assertNull(row.renameButtonForTest().getTooltip());
                assertTrue(row.renameButtonForTest().getStyleClass().contains("saved-project-save-button"));
                assertTrue(row.cancelButtonForTest().isVisible());
                assertEquals("Скасувати", row.cancelButtonForTest().getText());
                assertNull(row.cancelButtonForTest().getTooltip());

                row.cancelButtonForTest().fire();
                assertFalse(row.isEditing());
                assertEquals("", row.renameButtonForTest().getText());
                assertNotNull(row.renameButtonForTest().getTooltip());
                assertFalse(row.cancelButtonForTest().isVisible());
            } finally {
                row.dispose();
            }
        });
    }

    /** Перевіряє, що дія видалення зберігає callback після заміни напису графічним значком. */
    @Test
    void deleteIconStillInvokesTheProvidedAction() throws Exception {
        runOnFxThread(() -> {
            AtomicBoolean deleteInvoked = new AtomicBoolean();
            SavedProjectRowView row = createRow(
                    new LocalizationService(Locale.ENGLISH), deleteInvoked);
            try {
                row.deleteButtonForTest().fire();
                assertTrue(deleteInvoked.get());
            } finally {
                row.dispose();
            }
        });
    }

    /** Перевіряє наявність окремих правил наведення для червоної дії видалення та зеленої дії збереження. */
    @Test
    void stylesheetDefinesDeleteAndSaveHoverStates() throws Exception {
        try (InputStream stream = SavedProjectRowView.class.getResourceAsStream("/menu-enhancements.css")) {
            assertNotNull(stream, "Таблиця стилів меню має бути доступною в classpath");
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains(".saved-project-delete-button:hover"));
            assertTrue(css.contains("#642c30"));
            assertTrue(css.contains(".saved-project-save-button:hover"));
            assertTrue(css.contains("#245b3a"));
        }

        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("uk"), Locale.forLanguageTag("ru"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            assertTrue(bundle.containsKey("menu.rename"));
            assertTrue(bundle.containsKey("menu.delete"));
            assertFalse(bundle.containsKey("menu.rename_hint"));
            assertFalse(bundle.containsKey("menu.delete_hint"));
        }
    }

    /** Створює рядок збереженого проєкту з ізольованими callback-ами для перевірки керування кнопками. */
    private static SavedProjectRowView createRow(
            LocalizationService localization,
            AtomicBoolean deleteInvoked
    ) {
        ProjectDescriptor project = new ProjectDescriptor(
                "saved-row-test",
                "Мій проєкт",
                Path.of("saved-row-test.db"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        return new SavedProjectRowView(
                project,
                localization,
                ignored -> { },
                (original, name) -> new ProjectDescriptor(
                        original.id(), name, original.databasePath(), original.modifiedAt()),
                ignored -> deleteInvoked.set(true),
                () -> { });
    }

    /** Повертає текст підказки, якщо вона призначена кнопці. */
    private static String tooltipText(Button button) {
        Tooltip tooltip = button.getTooltip();
        return tooltip == null ? null : tooltip.getText();
    }

    /** Виконує операцію в JavaFX Application Thread і повертає помилки до JUnit-потоку. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Тест рядка збереженого проєкту завершився помилкою", failure[0]);
        }
    }
}
