package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronActivationMode;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.support.TooltipFactory;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Формує графічну підказку параметрів нейрона або структури, не змішуючи її компонування з логікою подання картки.
 * Вузли створюються один раз і повторно використовуються під час оновлення моделі та стану шухлядки.
 */
final class NeuronParameterTooltip {

    private static final String CONTENT_STYLE_CLASS = "parameter-tooltip-content";
    private static final String TITLE_STYLE_CLASS = "parameter-tooltip-title";
    private static final String SECTION_STYLE_CLASS = "parameter-tooltip-section-title";
    private static final String VALUE_STYLE_CLASS = "parameter-tooltip-value";
    private static final String DIVIDER_STYLE_CLASS = "parameter-tooltip-divider";
    private static final double DIVIDER_HEIGHT = 0.5;

    private final Tooltip tooltip = TooltipFactory.create("");
    private final VBox content = new VBox();
    private final Label titleLabel = createLabel(TITLE_STYLE_CLASS);
    private final Region titleDivider = createDivider();
    private final Label structureSectionLabel = createLabel(SECTION_STYLE_CLASS);
    private final Region structureDivider = createDivider();
    private final Label memberCountLabel = createLabel(VALUE_STYLE_CLASS);
    private final Label strongestNeuronLabel = createLabel(VALUE_STYLE_CLASS);
    private final Region neuronsTopDivider = createDivider();
    private final Label neuronsSectionLabel = createLabel(SECTION_STYLE_CLASS);
    private final Region neuronsDivider = createDivider();
    private final Label signalStrengthLabel = createLabel(VALUE_STYLE_CLASS);
    private final Label activationThresholdLabel = createLabel(VALUE_STYLE_CLASS);
    private final Label activeTicksLabel = createLabel(VALUE_STYLE_CLASS);
    private final Label delayLabel = createLabel(VALUE_STYLE_CLASS);

    private LocalizationService localization;

    /** Створює підказку з незмінним набором графічних елементів для повторного оновлення. */
    NeuronParameterTooltip() {
        content.getStyleClass().add(CONTENT_STYLE_CLASS);
        content.setFillWidth(true);
        titleLabel.setAlignment(Pos.CENTER);
        structureSectionLabel.setAlignment(Pos.CENTER);
        neuronsSectionLabel.setAlignment(Pos.CENTER);
        content.getChildren().setAll(
                titleLabel,
                titleDivider,
                structureSectionLabel,
                structureDivider,
                memberCountLabel,
                strongestNeuronLabel,
                neuronsTopDivider,
                neuronsSectionLabel,
                neuronsDivider,
                signalStrengthLabel,
                activationThresholdLabel,
                activeTicksLabel,
                delayLabel
        );
        tooltip.setText("");
        tooltip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tooltip.setGraphic(content);
    }

    /**
     * Налаштовує мову підписів підказки через спільну службу локалізації застосунку.
     *
     * @param localization служба локалізації, яка відповідає за активну мову інтерфейсу.
     * @throws NullPointerException якщо службу локалізації не задано.
     */
    void configureLocalization(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /** Прибирає посилання на службу локалізації під час звільнення подання нейрона. */
    void clearLocalization() {
        localization = null;
    }

    /**
     * Оновлює значення й видимість секцій відповідно до моделі та стану шухлядки структури.
     *
     * @param neuron модель нейрона або структури, для якої показується підказка.
     * @param memberCount кількість прямих учасників структури.
     * @param drawerExpanded {@code true}, якщо шухлядка структури відкрита.
     * @throws NullPointerException якщо модель нейрона не задано.
     */
    void refresh(Neuron neuron, int memberCount, boolean drawerExpanded) {
        Objects.requireNonNull(neuron, "neuron");
        boolean structure = neuron.type() == NeuronType.STRUCTURE;
        boolean showStructureSection = structure && !drawerExpanded;
        boolean showNeuronParameters = !structure || !drawerExpanded;

        titleLabel.setText(text("parameters_title", "Parameters"));
        structureSectionLabel.setText(text("structure_section", "Structure"));
        neuronsSectionLabel.setText(text("neurons_section", "Neurons"));
        memberCountLabel.setText(parameterLine(
                text("structure_neuron_count", "Neuron count"), Integer.toString(memberCount)));
        strongestNeuronLabel.setText(parameterLine(
                text("strongest_neuron", "Strongest neuron"),
                text(neuron.strongestNeuronOnly() ? "enabled" : "disabled",
                        neuron.strongestNeuronOnly() ? "On" : "Off")));
        refreshNeuronParameters(neuron);

        setNodeVisible(titleDivider, true);
        setNodeVisible(structureSectionLabel, showStructureSection);
        setNodeVisible(structureDivider, showStructureSection);
        setNodeVisible(memberCountLabel, structure);
        setNodeVisible(strongestNeuronLabel, structure);
        setNodeVisible(neuronsTopDivider, showStructureSection);
        setNodeVisible(neuronsSectionLabel, showStructureSection);
        setNodeVisible(neuronsDivider, showStructureSection);
        setNodeVisible(signalStrengthLabel, showNeuronParameters);
        setNodeVisible(activationThresholdLabel, showNeuronParameters);
        setNodeVisible(activeTicksLabel, showNeuronParameters);
        setNodeVisible(delayLabel, showNeuronParameters);

        StringBuilder renderedText = new StringBuilder(titleLabel.getText());
        if (structure) {
            if (showStructureSection) {
                appendLine(renderedText, structureSectionLabel.getText());
            }
            appendLine(renderedText, memberCountLabel.getText());
            appendLine(renderedText, strongestNeuronLabel.getText());
        }
        if (showNeuronParameters) {
            if (structure) {
                appendLine(renderedText, neuronsSectionLabel.getText());
            }
            appendLine(renderedText, signalStrengthLabel.getText());
            appendLine(renderedText, activationThresholdLabel.getText());
            appendLine(renderedText, activeTicksLabel.getText());
            appendLine(renderedText, delayLabel.getText());
        }
        tooltip.setText(renderedText.toString());
    }

    /** Повертає JavaFX-підказку, яку власник подання встановлює на інтерактивні вузли нейрона. */
    Tooltip tooltip() {
        return tooltip;
    }

    /** Повертає сформований текст для перевірок без відкриття окремого вікна підказки. */
    String textForTest() {
        return tooltip.getText();
    }

    /** Повертає графічний контейнер для перевірки структури й оформлення підказки. */
    VBox contentForTest() {
        return content;
    }

    /** Оновлює чотири спільні параметри нейрона, враховуючи відсотковий режим порога активації. */
    private void refreshNeuronParameters(Neuron neuron) {
        String signalLabel = localization == null
                ? "Signal strength"
                : localization.text("neuron.settings.signal_strength");
        String thresholdLabel = localization == null
                ? "Activation threshold"
                : localization.text("neuron.settings.activation_threshold");
        String thresholdValue = Integer.toString(neuron.activationThreshold());
        if (neuron.activationMode() == NeuronActivationMode.SIGNAL_COUNT) {
            thresholdValue += "%";
        }
        signalStrengthLabel.setText(parameterLine(signalLabel, Integer.toString(neuron.signalStrength())));
        activationThresholdLabel.setText(parameterLine(thresholdLabel, thresholdValue));
        activeTicksLabel.setText(parameterLine(
                text("active_ticks", "Active ticks"), Integer.toString(neuron.activeTicks())));
        delayLabel.setText(parameterLine(
                text("delay", "Delay"), Integer.toString(neuron.delayTicks())));
    }

    /** Створює текстовий вузол підказки з класом стилю та розтягуванням на ширину контейнера. */
    private static Label createLabel(String styleClass) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /** Створює тонкий горизонтальний роздільник між заголовком секції та її параметрами. */
    private static Region createDivider() {
        Region divider = new Region();
        divider.getStyleClass().add(DIVIDER_STYLE_CLASS);
        divider.setMinHeight(DIVIDER_HEIGHT);
        divider.setPrefHeight(DIVIDER_HEIGHT);
        divider.setMaxHeight(DIVIDER_HEIGHT);
        divider.setMaxWidth(Double.MAX_VALUE);
        return divider;
    }

    /** Вмикає або вимикає вузол разом із його місцем у компонуванні підказки. */
    private static void setNodeVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Додає текстовий рядок до підказки, відокремлюючи його від попереднього переносом рядка. */
    private static void appendLine(StringBuilder text, String line) {
        text.append('\n').append(line);
    }

    /** Отримує переклад із тематичного набору параметрів або використовує англійський резервний текст. */
    private String text(String key, String fallback) {
        return localization == null ? fallback : localization.textFrom("i18n.neuronSettings", key);
    }

    /** Форматує рядок параметра та видаляє зайву кінцеву двокрапку з локалізованої назви. */
    private static String parameterLine(String label, String value) {
        String normalizedLabel = label == null ? "" : label.strip();
        while (normalizedLabel.endsWith(":")) {
            normalizedLabel = normalizedLabel.substring(0, normalizedLabel.length() - 1).stripTrailing();
        }
        return normalizedLabel + ": " + value;
    }
}
