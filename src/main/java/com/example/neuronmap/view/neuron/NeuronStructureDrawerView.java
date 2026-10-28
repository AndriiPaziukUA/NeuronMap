package com.example.neuronmap.view.neuron;

import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.paint.Color;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Відображає впорядкований список учасників структури як висувну шухлядку під нейроном-контейнером. */
public final class NeuronStructureDrawerView extends Pane {

    private static final String STYLE_CLASS = "neuron-structure-drawer";

    private List<NeuronView> memberViews = List.of();
    private boolean expanded;

    /** Створює приховану шухлядку сталої ширини, яка дорівнює ширині тіла нейрона без портів. */
    public NeuronStructureDrawerView() {
        getStyleClass().add(STYLE_CLASS);
        setBackground(new Background(new BackgroundFill(Color.web("#242830"), null, null)));
        setBorder(new Border(new BorderStroke(
                Color.web("#25292f"), BorderStrokeStyle.SOLID, null,
                new BorderWidths(0.0, 2.0, 2.0, 2.0))));
        setManaged(false);
        setPickOnBounds(true);
        setMinWidth(NeuronView.WIDTH);
        setPrefWidth(NeuronView.WIDTH);
        setMaxWidth(NeuronView.WIDTH);
        setMinHeight(0.0);
        setPrefHeight(0.0);
        setVisible(false);
    }

    /**
     * Замінює відображений список учасників, зберігаючи порядок моделі та розміщуючи картки впритул.
     * Подання від'єднуються від попередніх контейнерів до додавання в цю шухлядку.
     *
     * @param members впорядковані подання нейронів структури.
     * @throws NullPointerException якщо список або один із його елементів не задано.
     * @throws IllegalArgumentException якщо той самий нейрон передано більше одного разу.
     */
    public void setMembers(List<NeuronView> members) {
        Objects.requireNonNull(members, "members");
        List<NeuronView> updatedMembers = List.copyOf(members);
        Set<String> memberIds = new LinkedHashSet<>();
        for (NeuronView member : updatedMembers) {
            if (!memberIds.add(member.model().id())) {
                throw new IllegalArgumentException("Structure drawer cannot display the same neuron twice");
            }
        }

        for (NeuronView previousMember : memberViews) {
            if (!updatedMembers.contains(previousMember)) {
                Parent parent = previousMember.getParent();
                if (parent == this || !(parent instanceof NeuronStructureDrawerView)) {
                    previousMember.setStructureMember(false);
                }
            }
        }
        for (NeuronView member : updatedMembers) {
            Parent parent = member.getParent();
            if (parent instanceof Pane pane && parent != this) {
                pane.getChildren().remove(member);
            }
        }
        getChildren().clear();
        memberViews = updatedMembers;
        for (NeuronView member : memberViews) {
            member.setStructureMember(true);
            getChildren().add(member);
        }
        refreshLayout();
        setVisible(expanded);
        refreshAncestorDrawers();
    }

    /** Перемикає стан шухлядки.
     * @return {@code true}, якщо шухлядка відкрита після перемикання.
     */
    public boolean toggleExpanded() {
        setExpanded(!expanded);
        return expanded;
    }

    /** Установлює стан відкриття без зміни списку учасників.
     * @param expanded {@code true}, щоб показати учасників; {@code false}, щоб сховати їх.
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        setVisible(expanded);
        refreshLayout();
        refreshAncestorDrawers();
    }

    /** Повертає стан відкриття шухлядки.
     * @return {@code true}, якщо учасники відображаються.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /** Повертає незмінний список подань у тому самому порядку, у якому вони показуються.
     * @return впорядкований незмінний список учасників.
     */
    public List<NeuronView> memberViews() {
        return memberViews;
    }

    /** Перераховує розмір шухлядки й укладає рядки впритул з урахуванням відкритих вкладених структур. */
    public void refreshLayout() {
        double height = 0.0;
        for (NeuronView member : memberViews) {
            height += member.displayedStructureRowHeight();
        }
        resizeRelocate(0.0, NeuronView.HEIGHT, NeuronView.WIDTH, height);
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(height);

        double rowY = 0.0;
        for (NeuronView member : memberViews) {
            member.resizeRelocate(0.0, rowY, NeuronView.WIDTH, NeuronView.HEIGHT);
            rowY += member.displayedStructureRowHeight();
        }
    }

    /** Оновлює висоту всіх предків-шухлядок після зміни складу або відкритості вкладеної структури. */
    private void refreshAncestorDrawers() {
        Parent ancestor = getParent();
        while (ancestor != null) {
            if (ancestor instanceof NeuronStructureDrawerView parentDrawer) {
                parentDrawer.refreshLayout();
            }
            ancestor = ancestor.getParent();
        }
    }
}
