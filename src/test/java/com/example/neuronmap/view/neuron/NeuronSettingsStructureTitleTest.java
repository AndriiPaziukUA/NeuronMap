package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє спеціальний заголовок структури без змін заголовка налаштувань звичайних нейронів. */
final class NeuronSettingsStructureTitleTest {

    /** Перевіряє, що заголовок залежить від типу редагованого нейрона та локалізується українською. */
    @Test
    void usesStructureSpecificTitleOnlyForStructureNeurons() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            StackPane root = new StackPane();
            new Scene(root, 900.0, 600.0);
            LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
            NeuronSettingsView view = new NeuronSettingsView(localization);

            view.showIn(root, new Neuron("ordinary", NeuronType.EXCITATORY, 0), ignored -> { });
            assertEquals("Налаштування нейрона", view.titleForTest().getText());
            view.hide();

            view.showIn(root, new Neuron("structure", NeuronType.STRUCTURE, 0), ignored -> { });
            assertEquals("Налаштування структури", view.titleForTest().getText());
            view.hide();
            view.dispose();
        });
    }
}
