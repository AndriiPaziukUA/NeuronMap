package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Створює знімки вибраних нейронів для копіювання та відновлює їх під час вставлення.
 * Для структур зберігає кількість учасників, спільний тип і режим найсильнішого нейрона.
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
     * Для структур зберігає кількість безпосередніх учасників, тип майбутніх учасників і режим конкуренції.
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
     * Для структур відновлює кількість учасників, спільний тип і режим найсильнішого нейрона.
     *
     * @param anchorX горизонтальна координата опорної точки вставлення.
     * @param anchorY вертикальна координата опорної точки вставлення.
     * @return вставлені нейрони в порядку знімка.
     * @throws IllegalStateException якщо спільні налаштування структури чи кількість учасників не вдалося відновити.
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
            if (item.structureMemberType() != null
                    && !neuronService.setStructureMemberType(neuron.id(), item.structureMemberType())) {
                throw new IllegalStateException(
                        "Could not restore the shared member type for pasted structure " + neuron.id());
            }
            if (item.type() == NeuronType.STRUCTURE
                    && !neuronService.setStructureStrongestNeuronOnly(neuron.id(), item.strongestNeuronOnly())) {
                throw new IllegalStateException(
                        "Could not restore strongest-neuron settings for pasted structure " + neuron.id());
            }
            if (item.structureMemberCount() != null) {
                restoreStructureMemberCount(neuron.id(), item.structureMemberCount());
            }
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
     * Створює описи нейронів, зберігаючи їх відносне положення відносно опорної точки копіювання.
     * Для структур фіксує тип учасників, кількість безпосередніх нейронів і режим конкуренції.
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
            NeuronStructure structure = structureOf(neuron);
            items.add(new NeuronData(
                    neuron.type(),
                    neuron.parameters(),
                    presentation.x() - anchorX,
                    presentation.y() - anchorY,
                    presentation.rotationDegrees(),
                    presentation.directionReversed(),
                    neuron.name(),
                    structure == null ? null : structure.settings().memberType(),
                    structure == null ? null : structure.memberIds().size(),
                    structure != null && structure.settings().strongestNeuronOnly()
            ));
        }
        return List.copyOf(items);
    }

    /**
     * Повертає контейнер структурного нейрона та перевіряє цілісність відповідного запису моделі.
     *
     * @param neuron нейрон, контейнер якого потрібно знайти.
     * @return структура або {@code null}, якщо нейрон не є структурою.
     * @throws IllegalStateException якщо для структурного нейрона відсутній контейнер.
     */
    private NeuronStructure structureOf(Neuron neuron) {
        if (neuron.type() != NeuronType.STRUCTURE) {
            return null;
        }
        NeuronStructure structure = neuronService.model().structure(neuron.id());
        if (structure == null) {
            throw new IllegalStateException("Missing structure settings for neuron " + neuron.id());
        }
        return structure;
    }

    /**
     * Відновлює задану кількість безпосередніх учасників новоствореної структури.
     * Стандартний початковий учасник видаляється, якщо вихідна структура була порожньою.
     *
     * @param structureId ідентифікатор новоствореної структури.
     * @param requestedCount кількість учасників у вихідній структурі.
     * @throws IllegalStateException якщо кількість неможливо відновити.
     */
    private void restoreStructureMemberCount(String structureId, int requestedCount) {
        NeuronStructure structure = neuronService.model().structure(structureId);
        if (structure == null) {
            throw new IllegalStateException("Missing structure settings for pasted neuron " + structureId);
        }
        if (requestedCount == 0) {
            for (String memberId : List.copyOf(structure.memberIds())) {
                if (neuronService.remove(memberId).isEmpty()) {
                    throw new IllegalStateException(
                            "Could not remove the initial member of pasted structure " + structureId);
                }
            }
            return;
        }
        if (!neuronService.ensureStructureMemberCount(structureId, requestedCount)) {
            throw new IllegalStateException(
                    "Could not restore the member count for pasted structure " + structureId);
        }
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
     * Зберігає параметри нейрона, положення, спільний тип учасників і режим конкуренції структури.
     *
     * @param type тип нейрона.
     * @param parameters параметри сигналу.
     * @param offsetX горизонтальне зміщення від опорної точки.
     * @param offsetY вертикальне зміщення від опорної точки.
     * @param rotationDegrees кут обертання нейрона в градусах.
     * @param directionReversed {@code true}, якщо напрямок портів нейрона розвернуто.
     * @param name назва нейрона або {@code null}.
     * @param structureMemberType спільний тип учасників структури або {@code null} для звичайного нейрона.
     * @param structureMemberCount кількість безпосередніх учасників або {@code null}, якщо значення не задано.
     * @param strongestNeuronOnly {@code true}, якщо структура вибирає лише найсильнішого отримувача сигналу.
     */
    public record NeuronData(
            NeuronType type,
            NeuronParameters parameters,
            double offsetX,
            double offsetY,
            double rotationDegrees,
            boolean directionReversed,
            String name,
            NeuronType structureMemberType,
            Integer structureMemberCount,
            boolean strongestNeuronOnly
    ) {
        /**
         * Перевіряє наявність типу й параметрів та узгодженість структурних даних у буфері.
         *
         * @throws NullPointerException якщо тип або параметри не задано.
         * @throws IllegalArgumentException якщо спільні налаштування чи кількість задані для звичайного нейрона,
         *                                  або кількість учасників від'ємна.
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
            if (structureMemberType != null
                    && (type != NeuronType.STRUCTURE || structureMemberType == NeuronType.STRUCTURE)) {
                throw new IllegalArgumentException("Only a structural neuron can carry its shared member type.");
            }
            if (structureMemberCount != null
                    && (type != NeuronType.STRUCTURE || structureMemberCount < 0)) {
                throw new IllegalArgumentException("Only a structural neuron can carry a non-negative member count.");
            }
            if (strongestNeuronOnly && type != NeuronType.STRUCTURE) {
                throw new IllegalArgumentException("Only a structural neuron can carry strongest-neuron settings.");
            }
        }

        /** Зберігає сумісність з повним попереднім контрактом, що не містив прапорця конкуренції.
         * @param type тип нейрона.
         * @param parameters параметри сигналу.
         * @param offsetX горизонтальне зміщення від опорної точки.
         * @param offsetY вертикальне зміщення від опорної точки.
         * @param rotationDegrees кут обертання.
         * @param directionReversed напрямок портів.
         * @param name назва нейрона.
         * @param structureMemberType спільний тип учасників структури.
         * @param structureMemberCount кількість учасників структури.
         */
        public NeuronData(
                NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                double rotationDegrees, boolean directionReversed, String name,
                NeuronType structureMemberType, Integer structureMemberCount
        ) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed,
                    name, structureMemberType, structureMemberCount, false);
        }

        /**
         * Зберігає сумісність із контрактом буфера, який містив спільний тип, але не кількість учасників.
         *
         * @param type тип нейрона.
         * @param parameters параметри нейрона.
         * @param offsetX горизонтальне зміщення.
         * @param offsetY вертикальне зміщення.
         * @param rotationDegrees кут обертання.
         * @param directionReversed стан напрямку портів.
         * @param name назва нейрона.
         * @param structureMemberType спільний тип учасників структури.
         */
        public NeuronData(
                NeuronType type,
                NeuronParameters parameters,
                double offsetX,
                double offsetY,
                double rotationDegrees,
                boolean directionReversed,
                String name,
                NeuronType structureMemberType
        ) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed,
                    name, structureMemberType, null, false);
        }

        /**
         * Створює дані буфера з попереднього контракту без спільного типу учасників структури.
         *
         * @param type тип нейрона.
         * @param parameters параметри сигналу.
         * @param offsetX горизонтальне зміщення.
         * @param offsetY вертикальне зміщення.
         * @param rotationDegrees кут обертання.
         * @param directionReversed напрямок портів.
         * @param name назва нейрона або {@code null}.
         */
        public NeuronData(
                NeuronType type,
                NeuronParameters parameters,
                double offsetX,
                double offsetY,
                double rotationDegrees,
                boolean directionReversed,
                String name
        ) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name, null, null, false);
        }

        /**
         * Створює дані буфера зі старого контракту без назви нейрона.
         *
         * @param type тип нейрона.
         * @param parameters параметри сигналу.
         * @param offsetX горизонтальне зміщення.
         * @param offsetY вертикальне зміщення.
         * @param rotationDegrees кут обертання.
         * @param directionReversed напрямок портів.
         */
        public NeuronData(
                NeuronType type,
                NeuronParameters parameters,
                double offsetX,
                double offsetY,
                double rotationDegrees,
                boolean directionReversed
        ) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, null, null, null, false);
        }

        /**
         * Створює дані буфера зі старого контракту з окремими числовими параметрами.
         *
         * @param type тип нейрона.
         * @param signalStrength сила сигналу.
         * @param activationThreshold поріг активації.
         * @param offsetX горизонтальне зміщення.
         * @param offsetY вертикальне зміщення.
         * @param rotationDegrees кут обертання.
         * @param directionReversed напрямок портів.
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
                    offsetX, offsetY, rotationDegrees, directionReversed, null, null, null, false);
        }

        /** Повертає силу сигналу для сумісності з наявними читачами даних буфера.
         * @return сила сигналу нейрона.
         */
        public int signalStrength() {
            return parameters.signalStrength();
        }

        /** Повертає поріг активації для сумісності з наявними читачами даних буфера.
         * @return поріг активації нейрона.
         */
        public int activationThreshold() {
            return parameters.activationThreshold();
        }
    }

    /** Містить пару нейрона та його подання, з якої створюється незмінний знімок для буфера. */
    private record NeuronSnapshotSource(Neuron neuron, NeuronPresentation presentation) {
        /** Перевіряє, що обидва елементи знімка існують.
         * @throws NullPointerException якщо нейрон або подання не задано.
         */
        private NeuronSnapshotSource {
            Objects.requireNonNull(neuron, "neuron");
            Objects.requireNonNull(presentation, "presentation");
        }
    }
}
