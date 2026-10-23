package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Створює знімок вибраних нейронів для копіювання та відновлює їх із відносними координатами під час вставлення.
 */
public final class NeuronClipboardService {

    private final NeuronService neuronService;
    private final GroupService groupService;
    private ClipboardContent content;

    /**
     * Створює службу копіювання нейронів і зберігає необхідні служби предметної моделі.
     *
     * @param neuronService служба операцій над нейронами.
     * @param groupService служба операцій над групами нейронів.
     * @throws NullPointerException якщо одну з обов'язкових служб не задано.
     */
    public NeuronClipboardService(NeuronService neuronService, GroupService groupService) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
    }

    /** Очищає внутрішній буфер копіювання нейронів. */
    public void clear() {
        content = null;
    }

    /**
     * Перевіряє, чи містить буфер дані для вставлення.
     *
     * @return {@code true}, якщо буфер містить хоча б один нейрон.
     */
    public boolean hasContent() {
        return content != null && !content.items().isEmpty();
    }

    /**
     * Повертає вміст буфера, якщо копіювання вже виконувалося.
     *
     * @return необов'язковий вміст буфера.
     */
    public Optional<ClipboardContent> content() {
        return Optional.ofNullable(content);
    }

    /**
     * Створює буферний знімок вибраних нейронів; для групового вибору зберігає групу цілком.
     *
     * @param selectedNeuronIds ідентифікатори вибраних нейронів.
     * @return {@code true}, якщо знайдено й скопійовано принаймні один нейрон.
     * @throws NullPointerException якщо набір ідентифікаторів не задано.
     */
    public boolean copy(Set<String> selectedNeuronIds) {
        Objects.requireNonNull(selectedNeuronIds, "selectedNeuronIds");
        if (selectedNeuronIds.isEmpty()) {
            clear();
            return false;
        }

        LinkedHashSet<String> selectedIds = new LinkedHashSet<>(selectedNeuronIds);
        boolean grouped = false;
        Set<String> idsToCopy;
        if (selectedIds.size() == 1) {
            String neuronId = selectedIds.getFirst();
            Optional<NeuronGroup> group = groupService.containing(neuronId);
            if (group.isPresent()) {
                idsToCopy = new LinkedHashSet<>(group.orElseThrow().memberIds());
                grouped = true;
            } else {
                idsToCopy = new LinkedHashSet<>(selectedIds);
            }
        } else {
            idsToCopy = new LinkedHashSet<>(selectedIds);
            Set<String> idsForGroupCheck = Set.copyOf(idsToCopy);
            grouped = neuronService.model().groups().stream()
                    .anyMatch(group -> group.memberIds().equals(idsForGroupCheck));
        }

        List<NeuronSnapshotSource> sources = idsToCopy.stream()
                .flatMap(id -> neuronService.find(id).stream())
                .flatMap(neuron -> neuronService.presentation(neuron.id())
                        .map(presentation -> new NeuronSnapshotSource(neuron, presentation))
                        .stream())
                .toList();
        if (sources.isEmpty()) {
            clear();
            return false;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (NeuronSnapshotSource source : sources) {
            NeuronPresentation presentation = source.presentation();
            minX = Math.min(minX, presentation.x());
            maxX = Math.max(maxX, presentation.x());
            minY = Math.min(minY, presentation.y());
            maxY = Math.max(maxY, presentation.y());
        }

        double anchorX = (minX + maxX) / 2.0;
        double anchorY = (minY + maxY) / 2.0;
        content = new ClipboardContent(createItems(sources, anchorX, anchorY), grouped);
        return true;
    }

    /**
     * Створює копії збережених нейронів і розміщує їх відносно заданої опорної точки.
     * Назва, параметри сигналу, напрямок і обертання копіюються з того самого знімка.
     *
     * @param anchorX горизонтальна координата опорної точки вставлення.
     * @param anchorY вертикальна координата опорної точки вставлення.
     * @return вставлені нейрони в порядку знімка.
     */
    public List<Neuron> paste(double anchorX, double anchorY) {
        if (!hasContent()) {
            return List.of();
        }

        LinkedHashSet<String> pastedIds = new LinkedHashSet<>();
        List<Neuron> pastedNeurons = new ArrayList<>(content.items().size());
        for (NeuronData item : content.items()) {
            Neuron neuron = neuronService.create(
                    item.type(),
                    anchorX + item.offsetX(),
                    anchorY + item.offsetY()
            );
            neuronService.updateSettings(neuron.id(), item.parameters(), item.name());
            neuronService.setRotation(neuron.id(), item.rotationDegrees());
            neuronService.setDirectionReversed(neuron.id(), item.directionReversed());
            pastedIds.add(neuron.id());
            pastedNeurons.add(neuron);
        }

        if (content.grouped() && pastedIds.size() >= 2) {
            groupService.create(pastedIds);
        }
        return List.copyOf(pastedNeurons);
    }

    /**
     * Містить скопійовані описи нейронів і ознаку відновлення їх як однієї групи.
     *
     * @param items дані нейронів, які потрібно вставити.
     * @param grouped {@code true}, якщо скопійовані нейрони утворювали одну групу.
     */
    public record ClipboardContent(List<NeuronData> items, boolean grouped) {
        /**
         * Створює незмінний вміст буфера копіювання.
         *
         * @throws NullPointerException якщо список елементів не задано.
         */
        public ClipboardContent {
            items = List.copyOf(items);
        }
    }

    /**
     * Створює описи нейронів, зберігаючи їх відносне положення відносно опорної точки.
     *
     * @param sources джерела, що пов'язують нейрони з їхніми поданнями.
     * @param anchorX горизонтальна координата опорної точки.
     * @param anchorY вертикальна координата опорної точки.
     * @return незмінний список даних для буфера обміну.
     */
    private List<NeuronData> createItems(List<NeuronSnapshotSource> sources, double anchorX, double anchorY) {
        List<NeuronData> items = new ArrayList<>(sources.size());
        for (NeuronSnapshotSource source : sources) {
            Neuron neuron = source.neuron();
            NeuronPresentation presentation = source.presentation();
            items.add(new NeuronData(
                    neuron.type(),
                    neuron.parameters(),
                    presentation.x() - anchorX,
                    presentation.y() - anchorY,
                    presentation.rotationDegrees(),
                    presentation.directionReversed(),
                    neuron.name()
            ));
        }
        return List.copyOf(items);
    }

    /**
     * Зберігає параметри нейрона, його назву та положення відносно опорної точки копіювання.
     *
     * @param type тип нейрона.
     * @param parameters налаштування сили сигналу, порога активації та активних тактів.
     * @param offsetX горизонтальне зміщення від опорної точки.
     * @param offsetY вертикальне зміщення від опорної точки.
     * @param rotationDegrees кут обертання нейрона в градусах.
     * @param directionReversed {@code true}, якщо напрямок портів нейрона розвернуто.
     * @param name назва нейрона або {@code null}.
     */
    public record NeuronData(
            NeuronType type,
            NeuronParameters parameters,
            double offsetX,
            double offsetY,
            double rotationDegrees,
            boolean directionReversed,
            String name
    ) {
        /**
         * Перевіряє наявність типу й параметрів для коректного опису нейрона в буфері.
         *
         * @throws NullPointerException якщо тип або параметри не задано.
         */
        public NeuronData {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(parameters, "parameters");
            if (name != null && name.codePointCount(0, name.length()) > Neuron.MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Neuron name exceeds the supported length.");
            }
            if (name != null && name.isEmpty()) {
                name = null;
            }
        }

        /**
         * Створює дані буфера з попереднього контракту без назви нейрона.
         *
         * @param type тип нейрона.
         * @param parameters параметри сигналу.
         * @param offsetX горизонтальне зміщення від опорної точки.
         * @param offsetY вертикальне зміщення від опорної точки.
         * @param rotationDegrees кут обертання нейрона в градусах.
         * @param directionReversed {@code true}, якщо напрямок портів нейрона розвернуто.
         */
        public NeuronData(
                NeuronType type,
                NeuronParameters parameters,
                double offsetX,
                double offsetY,
                double rotationDegrees,
                boolean directionReversed
        ) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, null);
        }

        /**
         * Створює дані буфера зі старого контракту з окремими значеннями параметрів.
         *
         * @param type тип нейрона.
         * @param signalStrength сила сигналу.
         * @param activationThreshold поріг активації.
         * @param offsetX горизонтальне зміщення від опорної точки.
         * @param offsetY вертикальне зміщення від опорної точки.
         * @param rotationDegrees кут обертання в градусах.
         * @param directionReversed {@code true}, якщо напрямок портів розвернуто.
         */
        public NeuronData(
                NeuronType type,
                int signalStrength,
                int activationThreshold,
                double offsetX,
                double offsetY,
                double rotationDegrees,
                boolean directionReversed
        ) {
            this(type, new NeuronParameters(signalStrength, activationThreshold),
                    offsetX, offsetY, rotationDegrees, directionReversed, null);
        }


        /**
         * Повертає силу сигналу для сумісності з наявними читачами даних буфера.
         *
         * @return сила сигналу нейрона.
         */
        public int signalStrength() {
            return parameters.signalStrength();
        }

        /**
         * Повертає поріг активації для сумісності з наявними читачами даних буфера.
         *
         * @return поріг активації нейрона.
         */
        public int activationThreshold() {
            return parameters.activationThreshold();
        }
    }

    /**
     * Пов'язує модель нейрона з його візуальним поданням на час створення копії.
     *
     * @param neuron семантичні дані нейрона.
     * @param presentation координати, поворот і напрямок подання цього нейрона.
     */
    private record NeuronSnapshotSource(Neuron neuron, NeuronPresentation presentation) {
    }
}
