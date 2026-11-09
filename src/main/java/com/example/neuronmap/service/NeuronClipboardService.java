package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronStructure;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.model.SignalType;
import com.example.neuronmap.model.StructureNeuronCreationMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Створює знімки вибраних нейронів для копіювання та відновлює їх під час вставлення.
 * Для адаптивних структур також переносить персональну силу кожного безпосереднього учасника.
 */
public final class NeuronClipboardService {
    private final NeuronService neuronService;
    private final GroupService groupService;
    private ClipboardContent content;

    /** Створює службу копіювання з власниками операцій над нейронами та групами. */
    public NeuronClipboardService(NeuronService neuronService, GroupService groupService) {
        this.neuronService = Objects.requireNonNull(neuronService, "neuronService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
    }

    /** Очищає внутрішній буфер копіювання. */
    public void clear() {
        content = null;
    }

    /** Перевіряє, чи містить буфер хоча б один нейрон. */
    public boolean hasContent() {
        return content != null && !content.items().isEmpty();
    }

    /** Повертає вміст буфера, якщо копіювання вже виконувалося. */
    public Optional<ClipboardContent> content() {
        return Optional.ofNullable(content);
    }

    /**
     * Створює знімок вибраних нейронів; для групового вибору копіює всю групу.
     *
     * @param selectedNeuronIds ідентифікатори вибраних нейронів.
     * @return {@code true}, якщо знайдено й скопійовано хоча б один нейрон.
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
                        .map(presentation -> new NeuronSnapshotSource(neuron, presentation)).stream())
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
        content = new ClipboardContent(createItems(sources, (minX + maxX) / 2.0, (minY + maxY) / 2.0), grouped);
        return true;
    }

    /**
     * Створює копії збережених нейронів, відновлює структури й розміщує їх відносно заданої точки.
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
            Neuron neuron = neuronService.create(item.type(), anchorX + item.offsetX(), anchorY + item.offsetY());
            neuronService.updateSettings(neuron.id(), item.parameters(), item.name());
            if (item.structureMemberType() != null
                    && !neuronService.setStructureMemberType(neuron.id(), item.structureMemberType())) {
                throw new IllegalStateException("Could not restore the shared member type for pasted structure " + neuron.id());
            }
            if (item.type() == NeuronType.STRUCTURE
                    && !neuronService.setStructureStrongestNeuronOnly(neuron.id(), item.strongestNeuronOnly())) {
                throw new IllegalStateException("Could not restore strongest-neuron settings for pasted structure " + neuron.id());
            }
            if (item.structureNeuronCreationMode() != null
                    && !neuronService.setStructureNeuronCreationMode(neuron.id(), item.structureNeuronCreationMode())) {
                throw new IllegalStateException("Could not restore neuron-creation settings for pasted structure " + neuron.id());
            }
            if (item.structureMemberCount() != null) {
                restoreStructureMemberCount(neuron.id(), item.structureMemberCount());
                restoreStructureMemberSignalStrengths(neuron.id(), item.structureMemberSignalStrengths());
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

    /** Створює дані буфера, включно з персональними силами учасників адаптивної структури. */
    private List<NeuronData> createItems(List<NeuronSnapshotSource> sources, double anchorX, double anchorY) {
        List<NeuronData> items = new ArrayList<>(sources.size());
        for (NeuronSnapshotSource source : sources) {
            Neuron neuron = source.neuron();
            NeuronPresentation presentation = source.presentation();
            NeuronStructure structure = structureOf(neuron);
            List<Integer> memberSignalStrengths = structure == null || neuron.signalType() == SignalType.STATIC
                    ? List.of()
                    : structure.memberIds().stream()
                            .map(memberId -> neuronService.find(memberId).orElseThrow().signalStrength())
                            .toList();
            items.add(new NeuronData(
                    neuron.type(), neuron.parameters(),
                    presentation.x() - anchorX, presentation.y() - anchorY,
                    presentation.rotationDegrees(), presentation.directionReversed(), neuron.name(),
                    structure == null ? null : structure.settings().memberType(),
                    structure == null ? null : structure.memberIds().size(),
                    structure != null && structure.settings().strongestNeuronOnly(),
                    structure == null ? null : structure.settings().neuronCreationMode(),
                    memberSignalStrengths
            ));
        }
        return List.copyOf(items);
    }

    /** Повертає контейнер структурного нейрона та перевіряє цілісність моделі. */
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

    /** Відновлює задану кількість безпосередніх учасників нової структури. */
    private void restoreStructureMemberCount(String structureId, int requestedCount) {
        NeuronStructure structure = neuronService.model().structure(structureId);
        if (structure == null) {
            throw new IllegalStateException("Missing structure settings for pasted neuron " + structureId);
        }
        if (requestedCount == 0) {
            for (String memberId : List.copyOf(structure.memberIds())) {
                if (neuronService.remove(memberId).isEmpty()) {
                    throw new IllegalStateException("Could not remove the initial member of pasted structure " + structureId);
                }
            }
            return;
        }
        if (!neuronService.ensureStructureMemberCount(structureId, requestedCount)) {
            throw new IllegalStateException("Could not restore the member count for pasted structure " + structureId);
        }
    }

    /** Повертає персональні сили адаптивних учасників після створення відповідного членства. */
    private void restoreStructureMemberSignalStrengths(String structureId, List<Integer> strengths) {
        if (strengths.isEmpty()) {
            return;
        }
        NeuronStructure structure = neuronService.model().structure(structureId);
        if (structure == null || structure.memberIds().size() != strengths.size()) {
            throw new IllegalStateException("Copied structure member strengths do not match its member count.");
        }
        List<String> memberIds = List.copyOf(structure.memberIds());
        for (int index = 0; index < strengths.size(); index++) {
            Neuron member = neuronService.find(memberIds.get(index)).orElseThrow();
            member.setSignalStrength(strengths.get(index));
        }
    }

    /** Незмінний вміст буфера копіювання. */
    public record ClipboardContent(List<NeuronData> items, boolean grouped) {
        /** Перевіряє та фіксує незмінний список скопійованих елементів. */
        public ClipboardContent {
            items = List.copyOf(items);
        }
    }

    /**
     * Зберігає параметри нейрона, положення та спільні параметри структури.
     *
     * @param type тип нейрона.
     * @param parameters параметри нейрона.
     * @param offsetX горизонтальне зміщення.
     * @param offsetY вертикальне зміщення.
     * @param rotationDegrees кут обертання.
     * @param directionReversed напрямок портів.
     * @param name назва або {@code null}.
     * @param structureMemberType спільний тип учасників або {@code null}.
     * @param structureMemberCount кількість прямих учасників або {@code null}.
     * @param strongestNeuronOnly режим вибору найсильнішого учасника.
     * @param structureNeuronCreationMode умова створення спільного нейрона або {@code null}.
     * @param structureMemberSignalStrengths персональні сили адаптивних безпосередніх учасників.
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
            boolean strongestNeuronOnly,
            StructureNeuronCreationMode structureNeuronCreationMode,
            List<Integer> structureMemberSignalStrengths
    ) {
        /** Перевіряє узгодженість даних скопійованого елемента та фіксує список персональних сил. */
        public NeuronData {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(parameters, "parameters");
            structureMemberSignalStrengths = List.copyOf(structureMemberSignalStrengths);
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
            if (structureNeuronCreationMode != null && type != NeuronType.STRUCTURE) {
                throw new IllegalArgumentException("Only a structural neuron can carry neuron-creation settings.");
            }
            if (!structureMemberSignalStrengths.isEmpty()
                    && (type != NeuronType.STRUCTURE || parameters.signalType() == SignalType.STATIC
                    || structureMemberCount == null
                    || structureMemberSignalStrengths.size() != structureMemberCount)) {
                throw new IllegalArgumentException("Adaptive member strengths must match an adaptive structure.");
            }
            if (structureMemberSignalStrengths.stream().anyMatch(value -> value == null || value <= 0)) {
                throw new IllegalArgumentException("Adaptive member strengths must be positive.");
            }
        }

        /** Зберігає сумісність із попереднім повним контрактом буфера без персональних сил. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed, String name,
                          NeuronType structureMemberType, Integer structureMemberCount, boolean strongestNeuronOnly,
                          StructureNeuronCreationMode structureNeuronCreationMode) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name,
                    structureMemberType, structureMemberCount, strongestNeuronOnly, structureNeuronCreationMode,
                    List.of());
        }

        /** Зберігає сумісність із контрактом без режиму створення нейронів. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed, String name,
                          NeuronType structureMemberType, Integer structureMemberCount, boolean strongestNeuronOnly) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name,
                    structureMemberType, structureMemberCount, strongestNeuronOnly, null, List.of());
        }

        /** Зберігає сумісність із контрактом без прапорця конкуренції. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed, String name,
                          NeuronType structureMemberType, Integer structureMemberCount) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name,
                    structureMemberType, structureMemberCount, false, null, List.of());
        }

        /** Зберігає сумісність із контрактом без кількості учасників. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed, String name,
                          NeuronType structureMemberType) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name,
                    structureMemberType, null, false, null, List.of());
        }

        /** Зберігає сумісність із контрактом без структурних параметрів. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed, String name) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, name,
                    null, null, false, null, List.of());
        }

        /** Зберігає сумісність зі старим контрактом без назви нейрона. */
        public NeuronData(NeuronType type, NeuronParameters parameters, double offsetX, double offsetY,
                          double rotationDegrees, boolean directionReversed) {
            this(type, parameters, offsetX, offsetY, rotationDegrees, directionReversed, null,
                    null, null, false, null, List.of());
        }

        /** Зберігає сумісність зі старим контрактом з окремими числовими параметрами. */
        public NeuronData(NeuronType type, int signalStrength, int activationThreshold,
                          double offsetX, double offsetY, double rotationDegrees, boolean directionReversed) {
            this(type, new NeuronParameters(signalStrength, activationThreshold),
                    offsetX, offsetY, rotationDegrees, directionReversed, null,
                    null, null, false, null, List.of());
        }

        /** Повертає ефективну силу сигналу скопійованого нейрона. */
        public int signalStrength() {
            return parameters.signalStrength();
        }

        /** Повертає поріг активації скопійованого нейрона. */
        public int activationThreshold() {
            return parameters.activationThreshold();
        }
    }

    /** Пов'язує модель нейрона з відповідним візуальним поданням для створення знімка. */
    private record NeuronSnapshotSource(Neuron neuron, NeuronPresentation presentation) {
        /** Перевіряє наявність обох складових знімка. */
        private NeuronSnapshotSource {
            Objects.requireNonNull(neuron, "neuron");
            Objects.requireNonNull(presentation, "presentation");
        }
    }
}
